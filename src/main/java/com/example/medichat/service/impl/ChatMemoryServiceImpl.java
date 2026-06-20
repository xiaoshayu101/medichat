package com.example.medichat.service.impl;

import com.example.medichat.service.ChatMemoryService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ChatMemoryServiceImpl implements ChatMemoryService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private ChatModel chatModel;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // 每20轮触发一次压缩
    private static final int COMPRESS_THRESHOLD = 20;

    private String buildKey(Long patientId, String sessionId) {
        return "chat:memory:" + patientId + ":" + sessionId;
    }

    @Override
    public void addMessage(Long patientId, String sessionId, String role, String content) {
        try {
            String key = buildKey(patientId, sessionId);
            Map<String, String> message = new HashMap<>();
            message.put("role", role);
            message.put("content", content);
            String json = objectMapper.writeValueAsString(message);
            stringRedisTemplate.opsForList().rightPush(key, json);

            // 检查是否达到压缩阈值
            int count = getMessageCount(patientId, sessionId);
            if (count >= COMPRESS_THRESHOLD) {
                compressSummary(patientId, sessionId);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public List<Map<String, String>> getMessages(Long patientId, String sessionId) {
        try {
            String key = buildKey(patientId, sessionId);
            List<String> jsonList = stringRedisTemplate.opsForList().range(key, 0, -1);
            List<Map<String, String>> messages = new ArrayList<>();
            if (jsonList == null) return messages;
            for (String json : jsonList) {
                Map<String, String> msg = objectMapper.readValue(json,
                        new TypeReference<Map<String, String>>() {});
                messages.add(msg);
            }
            return messages;
        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    @Override
    public int getMessageCount(Long patientId, String sessionId) {
        String key = buildKey(patientId, sessionId);
        Long size = stringRedisTemplate.opsForList().size(key);
        return size == null ? 0 : size.intValue();
    }

    @Override
    public void compressSummary(Long patientId, String sessionId) {
        try {
            String key = buildKey(patientId, sessionId);

            // 取出所有对话
            List<Map<String, String>> messages = getMessages(patientId, sessionId);
            if (messages.isEmpty()) return;

            // 把对话历史拼成文本，让LLM生成摘要
            StringBuilder dialogText = new StringBuilder();
            for (Map<String, String> msg : messages) {
                String role = "user".equals(msg.get("role")) ? "患者" : "AI";
                dialogText.append(role).append("：").append(msg.get("content")).append("\n");
            }

            String prompt = "以下是一段医疗问诊对话，请生成一段简洁的病情摘要（100字以内），" +
                    "重点包含：主诉、症状描述、持续时间、既往病史等关键医疗信息。\n\n" +
                    "对话内容：\n" + dialogText;

            String summary = chatModel.chat(prompt);

            // 清空Redis里的原有对话，替换成压缩后的摘要
            stringRedisTemplate.delete(key);

            // 把摘要作为一条"system"消息存回去，作为后续对话的上下文
            Map<String, String> summaryMessage = new HashMap<>();
            summaryMessage.put("role", "system");
            summaryMessage.put("content", "【历史对话摘要】" + summary);
            String summaryJson = objectMapper.writeValueAsString(summaryMessage);
            stringRedisTemplate.opsForList().rightPush(key, summaryJson);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void clearMemory(Long patientId, String sessionId) {
        String key = buildKey(patientId, sessionId);
        stringRedisTemplate.delete(key);
    }
}