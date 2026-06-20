package com.example.medichat.service;

import java.util.List;
import java.util.Map;

public interface ChatMemoryService {

    /**
     * 添加一轮对话到短期记忆
     * @param patientId 患者ID
     * @param sessionId 本次问诊会话ID（用appointmentId）
     * @param role      "user" 或 "assistant"
     * @param content   消息内容
     */
    void addMessage(Long patientId, String sessionId, String role, String content);

    /**
     * 获取当前所有短期记忆（按时间顺序）
     */
    List<Map<String, String>> getMessages(Long patientId, String sessionId);

    /**
     * 获取当前对话轮数
     */
    int getMessageCount(Long patientId, String sessionId);

    /**
     * 触发摘要压缩（把前N轮对话压缩成一段摘要，替换原有记录）
     */
    void compressSummary(Long patientId, String sessionId);

    /**
     * 清空这次问诊的短期记忆（问诊结束时调用）
     */
    void clearMemory(Long patientId, String sessionId);
}