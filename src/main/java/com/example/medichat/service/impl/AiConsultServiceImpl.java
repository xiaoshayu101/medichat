package com.example.medichat.service.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.medichat.entity.PatientSummary;
import com.example.medichat.mapper.PatientSummaryMapper;
import com.example.medichat.service.AiConsultService;
import com.example.medichat.service.ChatMemoryService;
import com.example.medichat.service.EsVectorService;
import com.xxl.job.core.handler.annotation.XxlJob;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class AiConsultServiceImpl implements AiConsultService {

    @Autowired
    private ChatModel chatModel;

    @Autowired
    private EmbeddingModel embeddingModel;

    @Autowired
    private EsVectorService esVectorService;

    @Autowired
    private ChatMemoryService chatMemoryService;

    @Autowired
    private PatientSummaryMapper patientSummaryMapper;

    @Autowired
    private ElasticsearchClient elasticsearchClient;

    @Override
    public String chat(Long patientId, String sessionId, String userMessage) {
        // 第一步：从Redis取出短期记忆
        List<Map<String, String>> shortTermMemory =
                chatMemoryService.getMessages(patientId, sessionId);

        // 第二步：用当前消息生成向量，去ES检索历史摘要Top3
        List<String> historySummaries = esVectorService.searchSimilar(patientId, userMessage, 3);


        // 第三步：拼历史摘要文本
        StringBuilder historySummary = new StringBuilder();
        if (!historySummaries.isEmpty()) {
            historySummary.append("【该患者的历史病情摘要】\n");
            for (String s : historySummaries) {
                historySummary.append("- ").append(s).append("\n");
            }
        }

        // 第四步：拼短期记忆文本
        StringBuilder shortTermText = new StringBuilder();
        if (!shortTermMemory.isEmpty()) {
            shortTermText.append("【本次问诊对话历史】\n");
            for (Map<String, String> msg : shortTermMemory) {
                String role = "user".equals(msg.get("role")) ? "患者"
                        : "system".equals(msg.get("role")) ? "背景" : "AI";
                shortTermText.append(role).append("：")
                        .append(msg.get("content")).append("\n");
            }
        }

        // 第五步：拼完整Prompt
        String systemPrompt = "你是一位专业的医疗预问诊AI助手，负责在患者就诊前收集病情信息。" +
                "请用简洁、专业、有温度的语言与患者对话，重点收集：主诉、症状详情、" +
                "持续时间、加重/缓解因素、既往病史、用药史、过敏史。\n\n" +
                historySummary +
                shortTermText +
                "请根据以上背景信息，回复患者的当前描述：";

        String fullPrompt = systemPrompt + "\n\n患者：" + userMessage;

        // 第六步：调LLM
        String aiReply = chatModel.chat(fullPrompt);

        // 第七步：把这轮对话存进Redis短期记忆
        chatMemoryService.addMessage(patientId, sessionId, "user", userMessage);
        chatMemoryService.addMessage(patientId, sessionId, "assistant", aiReply);

        return aiReply;
    }

    @Override
    public void finishConsult(Long patientId, String sessionId) {
        // 第一步：取出完整的短期记忆
        List<Map<String, String>> messages =
                chatMemoryService.getMessages(patientId, sessionId);
        if (messages.isEmpty()) return;

        // 第二步：让LLM生成本次问诊的结构化摘要
        StringBuilder dialogText = new StringBuilder();
        for (Map<String, String> msg : messages) {
            String role = "user".equals(msg.get("role")) ? "患者" : "AI";
            dialogText.append(role).append("：")
                    .append(msg.get("content")).append("\n");
        }

        String summaryPrompt = "请根据以下问诊对话，生成一份结构化的病情摘要，" +
                "格式为：【主诉】【症状】【持续时间】【既往史】【用药过敏史】，" +
                "每项内容简洁精准，总字数不超过200字。\n\n" + dialogText;

        String summary = chatModel.chat(summaryPrompt);

        // 第三步：把摘要原文存进MySQL
        PatientSummary patientSummary = new PatientSummary();
        patientSummary.setPatientId(patientId);
        patientSummary.setSummary(summary);
        patientSummaryMapper.insert(patientSummary);

        // 第四步：把摘要向量化，存进ES
        Metadata metadata = new Metadata();
        metadata.put("patientId", String.valueOf(patientId));
        metadata.put("summaryId", String.valueOf(patientSummary.getId()));

        String esDocId = esVectorService.saveVector(patientId, patientSummary.getId(), summary);
        // 第五步：把ES文档ID回写到MySQL，方便后续关联
        patientSummary.setEsDocId(esDocId);
        patientSummaryMapper.updateById(patientSummary);

        // 第六步：清空Redis短期记忆
        chatMemoryService.clearMemory(patientId, sessionId);
    }
    @Override
    @XxlJob("compressLongTermMemoryJob")
    public void compressLongTermMemory() {
        // 找出摘要超过20条的患者
        List<Map<String, Object>> patients =
                patientSummaryMapper.selectPatientsWithTooManySummaries();

        if (patients.isEmpty()) {
            log.info("没有需要二次压缩的患者");
            return;
        }

        for (Map<String, Object> row : patients) {
            Long patientId = Long.valueOf(row.get("patient_id").toString());
            compressPatientSummaries(patientId);
        }
    }

    /**
     * 对单个患者的所有历史摘要进行二次压缩，合并成一份健康画像
     */
    private void compressPatientSummaries(Long patientId) {
        try {
            // 查出这个患者所有历史摘要
            List<PatientSummary> summaries = patientSummaryMapper.selectList(
                    new QueryWrapper<PatientSummary>()
                            .eq("patient_id", patientId)
                            .orderByAsc("create_time")
            );

            if (summaries.isEmpty()) return;

            // 把所有摘要拼在一起
            StringBuilder allSummaries = new StringBuilder();
            for (PatientSummary s : summaries) {
                allSummaries.append(s.getSummary()).append("\n---\n");
            }

            // 让LLM合并成健康画像
            String prompt = "以下是同一位患者多次就诊的病情摘要记录，" +
                    "请将这些记录合并整理成一份结构化的患者健康画像，" +
                    "格式为：【基本健康状况】【慢性病史】【用药过敏史】【近期主要症状】，" +
                    "去除重复信息，突出关键病情，总字数不超过300字。\n\n" +
                    allSummaries;

            String healthProfile = chatModel.chat(prompt);

            // 删除旧的所有摘要记录（MySQL）
            patientSummaryMapper.delete(
                    new QueryWrapper<PatientSummary>()
                            .eq("patient_id", patientId)
            );

            // 同时删掉ES里这个患者的所有旧向量
            for (PatientSummary s : summaries) {
                if (s.getEsDocId() != null) {
                    try {
                        elasticsearchClient.delete(d -> d
                                .index("medical-summaries")
                                .id(s.getEsDocId())
                        );
                    } catch (Exception e) {
                        log.error("删除ES文档失败，esDocId={}", s.getEsDocId(), e);
                    }
                }
            }

            // 把合并后的健康画像作为新的单条摘要存入MySQL+ES
            PatientSummary healthProfileSummary = new PatientSummary();
            healthProfileSummary.setPatientId(patientId);
            healthProfileSummary.setSummary(healthProfile);
            patientSummaryMapper.insert(healthProfileSummary);

            String esDocId = esVectorService.saveVector(
                    patientId,
                    healthProfileSummary.getId(),
                    healthProfile
            );
            healthProfileSummary.setEsDocId(esDocId);
            patientSummaryMapper.updateById(healthProfileSummary);

            log.info("患者 {} 的健康画像压缩完成", patientId);


        } catch (Exception e) {
            log.error("患者 {} 压缩失败: ", patientId, e);
        }
    }
    /**
     * 生成预问诊摘要（不存储）
     */
    @Override
    public String generatePreConsultationSummary(Long patientId, String sessionId) {
        try {
            // 从Redis取出短期记忆
            List<Map<String, String>> shortTermMemory =
                    chatMemoryService.getMessages(patientId, sessionId);

            // 拼接对话文本
            StringBuilder dialogText = new StringBuilder();
            for (Map<String, String> msg : shortTermMemory) {
                String role = "user".equals(msg.get("role")) ? "患者" : "AI";
                dialogText.append(role).append("：")
                        .append(msg.get("content")).append("\n");
            }

            // 生成摘要
            String summaryPrompt = "请根据以下问诊对话，生成一份结构化的病情摘要，" +
                    "格式为：【主诉】【症状】【持续时间】【既往史】【用药过敏史】，" +
                    "每项内容简洁精准，总字数不超过200字。\n\n" + dialogText;

            String summaryContent = chatModel.chat(summaryPrompt);
            // 将预问诊摘要存入MySQL，供医生端拉取兜底
            if (summaryContent != null) {
                PatientSummary patientSummary = new PatientSummary();
                patientSummary.setPatientId(patientId);
                patientSummary.setSummary(summaryContent);
                patientSummaryMapper.insert(patientSummary);
            }
            return summaryContent;

        } catch (Exception e) {
            log.error("生成预问诊摘要失败, patientId={}, sessionId={}, 原因: ", patientId, sessionId, e);
            return null;
        }

    }

}