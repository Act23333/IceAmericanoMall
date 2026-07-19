package org.icedAmericanoMall.rag;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * V2.5.2: RAG 检索器 — 向量检索 + 上下文组装。
 * <p>
 * 调用 search-service 内部向量搜索接口，将检索结果格式化为 LLM 可用的上下文文本。
 * <p>
 * V3.0 升级方向：多路召回（向量+BM25+结构化过滤）+ RRF 融合 + BGE-reranker 重排序。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "ai.enabled", havingValue = "true")
public class RAGRetriever {

    private final EmbeddingService embeddingService;
    private final RestTemplate restTemplate = new RestTemplate();

    private static final int TOP_K = 5;

    /**
     * 检索与查询语义相关的商品/FAQ 上下文。
     *
     * @param query 用户问题（中文自然语言）
     * @return 格式化的上下文文本，供 LLM System Prompt 使用
     */
    @SuppressWarnings("unchecked")
    public String retrieve(String query) {
        try {
            double[] embedding = embeddingService.embed(query);
            if (embedding.length == 0) {
                return "（暂无相关上下文）";
            }

            Map<String, Object> requestBody = Map.of(
                    "embedding", embedding,
                    "size", TOP_K
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

            ResponseEntity<List> response = restTemplate.postForEntity(
                    "http://search-service/internal/search/vector",
                    request,
                    List.class);

            List<Map<String, Object>> results = response.getBody();
            if (results == null || results.isEmpty()) {
                return "（暂无相关上下文）";
            }

            StringBuilder context = new StringBuilder();
            context.append("以下是与用户问题相关的商品信息：\n");
            for (int i = 0; i < results.size(); i++) {
                Map<String, Object> item = results.get(i);
                context.append(String.format("%d. %s (ID:%s) — ¥%.2f 销量:%s\n   描述:%s\n",
                        i + 1,
                        item.get("name"),
                        item.get("productId"),
                        safeGetDouble(item, "price") / 100,
                        item.get("soldCount"),
                        item.get("description")));
            }
            return context.toString();
        } catch (Exception e) {
            log.warn("RAG retrieval failed: {}", e.getMessage());
            return "（暂无相关上下文）";
        }
    }

    private static double safeGetDouble(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return 0;
    }
}
