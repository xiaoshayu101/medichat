package com.example.medichat.service;

public interface AiConsultService {

    /**
     * 预问诊对话
     * @param patientId 患者ID
     * @param sessionId 本次问诊会话ID（用appointmentId）
     * @param userMessage 患者输入的消息
     * @return AI回复
     */
    String chat(Long patientId, String sessionId, String userMessage);

    /**
     * 问诊结束：生成本次摘要，存入长期记忆（MySQL+ES）
     */
    void finishConsult(Long patientId, String sessionId);

    /**
     * 对摘要超过20条的患者，触发二次压缩，合并成健康画像
     * 由XXL-Job每周触发
     */
    void compressLongTermMemory();

    String generatePreConsultationSummary(Long patientId, String sessionId);
}