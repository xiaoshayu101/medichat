package com.example.medichat.service.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.IndexResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.example.medichat.service.EsVectorService;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class EsVectorServiceImpl implements EsVectorService {

    @Autowired
    private ElasticsearchClient elasticsearchClient;

    @Autowired
    private EmbeddingModel embeddingModel;

    private static final Logger log = LoggerFactory.getLogger(EsVectorServiceImpl.class);

    @Value("${elasticsearch.index-name}")
    private String indexName;

    @Override
    public String saveVector(Long patientId, Long summaryId, String summaryText) {
        try {
            // 生成向量
            Embedding embedding = embeddingModel.embed(summaryText).content();
            float[] vector = embedding.vector();

            // 构建ES文档
            Map<String, Object> document = new HashMap<>();
            document.put("patientId", patientId);
            document.put("summaryId", summaryId);
            document.put("summaryText", summaryText);
            document.put("vector", vector);

            // 存入ES
            IndexResponse response = elasticsearchClient.index(i -> i
                    .index(indexName)
                    .document(document)
            );

            return response.id();
        } catch (Exception e) {
            log.error("保存向量数据失败, patientId: {}, summaryId: {}", patientId, summaryId, e);
            return null;
        }
    }

    @Override
    public List<String> searchSimilar(Long patientId, String queryText, int topN) {
        List<String> results = new ArrayList<>();
        try {
            // 生成查询向量
            Embedding queryEmbedding = embeddingModel.embed(queryText).content();
            float[] queryVector = queryEmbedding.vector();

            // 用ES的knn查询
            SearchResponse<Map> response = elasticsearchClient.search(s -> s
                            .index(indexName)
                            .knn(k -> k
                                    .field("vector")
                                    .queryVector(toFloatList(queryVector))
                                    .k(topN)
                                    .numCandidates(topN * 10)
                                    .filter(f -> f
                                            .term(t -> t
                                                    .field("patientId")
                                                    .value(String.valueOf(patientId))
                                            )
                                    )
                            ),
                    Map.class
            );

            for (Hit<Map> hit : response.hits().hits()) {
                if (hit.source() != null) {
                    Object text = hit.source().get("summaryText");
                    if (text != null) {
                        results.add(text.toString());
                    }
                }
            }
        } catch (Exception e) {
            log.error("查询相似向量失败, patientId: {}, queryText: {}", patientId, queryText, e);
        }
        return results;
    }

    private List<Float> toFloatList(float[] floats) {
        List<Float> list = new ArrayList<>();
        for (float f : floats) {
            list.add(f);
        }
        return list;
    }
}