package org.icedamericanomall.rag;

import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.client.SearchClient;
import org.noLazy.common.domain.Result;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

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
    private final RestClient restClient;

    private static final int KNN_CANDIDATES = 50;
    private static final int BM25_CANDIDATES = 30;
    private static final int RRF_K = 60;
    private static final int TOP_K = 5;

    public RAGRetriever(EmbeddingService embeddingService, RerankerService rerankerService,
                        SearchClient searchClient, RestClient restClient) {
        this.embeddingService = embeddingService;
        this.rerankerService = rerankerService;
        this.searchClient = searchClient;
        this.restClient = restClient;
    }

    /**
     * V3.4: 限定商家知识库的检索 — 仅查询该 seller 下的 FAQ/政策/手册。
     */
    public String retrieveForSeller(String query, Long sellerId) {
        try {
            double[] embedding = embeddingService.embed(query);
            if (embedding.length == 0) return "（暂无本店相关知识）";

            Map<String, Object> body = Map.of("embedding", embedding, "size", TOP_K,
                    "seller_id", sellerId); // 增加 seller 过滤
            List<Map<String, Object>> results = restClient.post()
                    .uri("http://search-service/internal/search/vector")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(MAP_LIST_TYPE);
            if (results == null || results.isEmpty()) return "（暂无本店相关知识）";
            return assembleContext(results);
        } catch (Exception e) {
            log.warn("Seller RAG failed: {}", e.getMessage());
            return "（暂无本店相关知识）";
        }
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

    private static final ParameterizedTypeReference<List<Map<String, Object>>> MAP_LIST_TYPE =
            new ParameterizedTypeReference<>() {};

    private List<Map<String, Object>> vectorRecall(String query) {
        try {
            double[] embedding = embeddingService.embed(query);
            if (embedding.length == 0) return Collections.emptyList();

            Map<String, Object> body = Map.of("embedding", embedding, "size", KNN_CANDIDATES);
            List<Map<String, Object>> result = restClient.post()
                    .uri("http://search-service/internal/search/vector")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(MAP_LIST_TYPE);
            return result != null ? result : Collections.emptyList();
        } catch (Exception e) {
            log.warn("Vector recall failed: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<Map<String, Object>> keywordRecall(String query) {
        try {
            Result<Map<String, Object>> result = searchClient.searchProducts(query, BM25_CANDIDATES);
            if (result == null || result.getData() == null) return Collections.emptyList();
            Object raw = result.getData().get("records");
            if (raw instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Map) {
                @SuppressWarnings({"unchecked", "rawtypes"})
                List<Map<String, Object>> records = (List) list;
                return records;
            }
            return Collections.emptyList();
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
