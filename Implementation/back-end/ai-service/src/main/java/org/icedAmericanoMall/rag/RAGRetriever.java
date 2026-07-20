package org.icedAmericanoMall.rag;

import lombok.extern.slf4j.Slf4j;
import org.icedAmericanoMall.client.SearchClient;
import org.noLazy.common.domain.Result;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * V3.0.0: 增强 RAG 检索器 — 多路召回 + RRF 融合 + 重排序 + 上下文组装。
 * <p>
 * 管线：Query → Embedding → [kNN召回 + BM25召回] → RRF融合 → Rerank → Assembly
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "ai.enabled", havingValue = "true")
public class RAGRetriever {

    private final EmbeddingService embeddingService;
    private final RerankerService rerankerService;
    private final SearchClient searchClient;
    private final RestTemplate restTemplate = new RestTemplate();

    private static final int KNN_CANDIDATES = 50;
    private static final int BM25_CANDIDATES = 30;
    private static final int RRF_K = 60;
    private static final int TOP_K = 5;

    public RAGRetriever(EmbeddingService embeddingService, RerankerService rerankerService,
                        SearchClient searchClient) {
        this.embeddingService = embeddingService;
        this.rerankerService = rerankerService;
        this.searchClient = searchClient;
    }

    /**
     * 检索与查询相关的上下文（完整 5 阶段管线）。
     */
    public String retrieve(String query) {
        try {
            // Stage 1: Query Rewrite (可选，后期增强)
            String rewrittenQuery = query;

            // Stage 2: 多路召回
            List<Map<String, Object>> knnResults = vectorRecall(rewrittenQuery);
            List<Map<String, Object>> bm25Results = keywordRecall(rewrittenQuery);

            // Stage 3: RRF 融合
            List<Map<String, Object>> fused = rrfFusion(knnResults, bm25Results);

            // Stage 4: 重排序
            List<Map<String, Object>> reranked = rerank(query, fused, TOP_K);

            // Stage 5: 上下文组装
            return assembleContext(reranked);

        } catch (Exception e) {
            log.warn("RAG retrieval failed: {}", e.getMessage());
            return "（暂无相关上下文）";
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> vectorRecall(String query) {
        try {
            double[] embedding = embeddingService.embed(query);
            if (embedding.length == 0) return Collections.emptyList();

            Map<String, Object> body = Map.of("embedding", embedding, "size", KNN_CANDIDATES);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            ResponseEntity<List> resp = restTemplate.postForEntity(
                    "http://search-service/internal/search/vector",
                    new HttpEntity<>(body, headers), List.class);
            return resp.getBody() != null ? resp.getBody() : Collections.emptyList();
        } catch (Exception e) {
            log.warn("Vector recall failed: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> keywordRecall(String query) {
        try {
            Result<Map<String, Object>> result = searchClient.searchProducts(query, BM25_CANDIDATES);
            if (result == null || result.getData() == null) return Collections.emptyList();
            Object records = result.getData().get("records");
            return records instanceof List ? (List<Map<String, Object>>) records : Collections.emptyList();
        } catch (Exception e) {
            log.warn("Keyword recall failed: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Reciprocal Rank Fusion (RRF) — 融合多个召回源的结果。
     * score(d) = Σ 1/(k + rank_i(d)), k=60
     */
    private List<Map<String, Object>> rrfFusion(List<Map<String, Object>> listA,
                                                 List<Map<String, Object>> listB) {
        Map<String, Double> scores = new LinkedHashMap<>();
        Map<String, Map<String, Object>> docMap = new LinkedHashMap<>();

        addRRFScores(listA, scores, docMap, "productId");
        addRRFScores(listB, scores, docMap, "productId");

        return scores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .map(e -> docMap.get(e.getKey()))
                .filter(Objects::nonNull)
                .toList();
    }

    private void addRRFScores(List<Map<String, Object>> docs,
                               Map<String, Double> scores,
                               Map<String, Map<String, Object>> docMap,
                               String idField) {
        for (int i = 0; i < docs.size(); i++) {
            Object idObj = docs.get(i).get(idField);
            if (idObj == null) continue;
            String id = idObj.toString();
            double rrfScore = 1.0 / (RRF_K + i + 1);
            scores.merge(id, rrfScore, Double::sum);
            docMap.putIfAbsent(id, docs.get(i));
        }
    }

    /**
     * 重排序 — 使用 BGE-reranker (或降级为启发式排序)。
     */
    private List<Map<String, Object>> rerank(String query, List<Map<String, Object>> docs, int topK) {
        if (docs.size() <= topK) return docs;

        List<String> candidates = docs.stream()
                .map(d -> formatDocForReranking(d))
                .toList();
        List<Integer> rankedIndices = rerankerService.rerank(query, candidates, topK);

        List<Map<String, Object>> result = new ArrayList<>();
        for (int idx : rankedIndices) {
            if (idx < docs.size()) result.add(docs.get(idx));
        }
        return result;
    }

    private String formatDocForReranking(Map<String, Object> doc) {
        return String.format("%s %s", doc.get("name"), doc.get("description"));
    }

    private String assembleContext(List<Map<String, Object>> results) {
        if (results.isEmpty()) return "（暂无相关上下文）";
        StringBuilder sb = new StringBuilder("以下是与用户问题相关的信息：\n");
        for (int i = 0; i < results.size(); i++) {
            Map<String, Object> item = results.get(i);
            sb.append(String.format("%d. %s (ID:%s) — ¥%.2f 销量:%s\n",
                    i + 1,
                    item.get("name"),
                    item.get("productId"),
                    safeGetDouble(item, "price") / 100,
                    item.get("soldCount")));
        }
        return sb.toString();
    }

    private static double safeGetDouble(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value instanceof Number ? ((Number) value).doubleValue() : 0;
    }
}
