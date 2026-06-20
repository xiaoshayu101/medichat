package com.example.medichat.service;

import java.util.List;

public interface EsVectorService {

    /**
     * 存入一条摘要向量
     * @param patientId 患者ID
     * @param summaryId MySQL里摘要记录的ID
     * @param summaryText 摘要原文
     * @return ES文档ID
     */
    String saveVector(Long patientId, Long summaryId, String summaryText);

    /**
     * 用文本检索该患者的历史摘要，返回Top3最相似的摘要原文
     */
    List<String> searchSimilar(Long patientId, String queryText, int topN);
}