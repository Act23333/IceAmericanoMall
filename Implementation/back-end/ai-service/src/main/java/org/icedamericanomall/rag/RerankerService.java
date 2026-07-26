package org.icedamericanomall.rag;

import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.config.AiProperties;
import org.icedamericanomall.dto.RerankerResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.*;

/**
 * V3.0.0: BGE-reranker 重排序服务。
 * <p>
 * 调用 BGE-reranker-v2-base Docker 服务（FastAPI + sentence-transformers）
 * 对多路召回结果进行语义重排序，将 Top-50 精排为 Top-5。
 * <p>
 * 如果 reranker 未启用，退化为简单的 TF-IDF 启发式排序。
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "ai.enabled", havingValue = "true")
public class RerankerService {

    private final RestClient restClient;
    private final AiProperties aiProperties;

    public RerankerService(AiProperties aiProperties, RestClient restClient) {
        this.aiProperties = aiProperties;
        this.restClient = restClient;
    }

    /**
     * 对候选文档列表进行重排序。
     *
     * @param query      用户原始查询
     * @param candidates 候选文档（文本列表）
     * @param topK       返回 Top-K 数量
     * @return 按相关性降序排列的文档索引（0-based）
     */
    public List<Integer> rerank(String query, List<String> candidates, int topK) {
        if (candidates.isEmpty()) return Collections.emptyList();

        if (!aiProperties.getRag().getReranker().isEnabled()) {
            return heuristicRerank(query, candidates, topK);
        }

        try {
            String url = aiProperties.getRag().getReranker().getBaseUrl() + "/rerank";
            Map<String, Object> body = Map.of("query", query, "documents", candidates, "top_k", topK);

            RerankerResponse resp = restClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(RerankerResponse.class);
            if (resp != null && resp.results() != null) {
                return resp.results().stream()
                        .map(RerankerResponse.RerankedDocument::index)
                        .limit(topK)
                        .toList();
            }
        } catch (Exception e) {
            log.warn("BGE-reranker call failed, falling back to heuristic: {}", e.getMessage());
        }
        return heuristicRerank(query, candidates, topK);
    }

    /**
     * 启发式重排序：按 query 关键词在文档中出现的频率排序。
     * BGE-reranker 不可用时的降级方案。
     */
    private List<Integer> heuristicRerank(String query, List<String> candidates, int topK) {
        String[] keywords = query.split("\\s+");
        record ScoredDoc(int index, int score) {}
        List<ScoredDoc> scored = new ArrayList<>();
        for (int i = 0; i < candidates.size(); i++) {
            String doc = candidates.get(i).toLowerCase();
            int score = 0;
            for (String kw : keywords) {
                int idx = 0;
                while ((idx = doc.indexOf(kw.toLowerCase(), idx)) != -1) {
                    score++;
                    idx += kw.length();
                }
            }
            scored.add(new ScoredDoc(i, score));
        }
        scored.sort((a, b) -> Integer.compare(b.score, a.score));
        return scored.stream()
                .filter(s -> s.score > 0)
                .limit(topK)
                .map(ScoredDoc::index)
                .toList();
    }
}
