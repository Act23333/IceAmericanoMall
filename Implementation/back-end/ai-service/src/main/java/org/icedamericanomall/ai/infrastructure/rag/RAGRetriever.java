package org.icedamericanomall.ai.infrastructure.rag;

import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.ai.infrastructure.config.AiProperties;
import org.icedamericanomall.ai.domain.model.knowledge.KnowledgeHit;
import org.icedamericanomall.ai.domain.model.product.ProductHit;
import org.icedamericanomall.ai.domain.port.SearchPort;
import org.icedamericanomall.ai.domain.port.VectorSearchPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * V3.0.0: 增强 RAG 检索器 — 多路召回 + RRF 融合 + 重排序 + 上下文组装。
 * <p>
 * 管线：Query → Embedding → [向量召回 + 关键词召回] → RRF 融合 → Rerank → Assembly。
 *
 * <p><b>接线状态（V5.4）</b>：知识库（{@code merchant_knowledge_base}）在
 * {@code ai.rag.enabled=true} 时经 search-service 建立 {@code knowledge_vectors} 索引，
 * {@link #retrieveForSeller} 已接入客服应答链路（CsManager 拼接上下文）。
 * 商品召回管线 {@link #retrieve(String)} 仍由客服 Agent 的 {@code RAGSearchTool} 调用。
 *
 * <p>向量与关键词召回均通过领域端口访问下游，服务地址与索引名不落代码。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "ai.enabled", havingValue = "true")
public class RAGRetriever {

    /** 向量召回候选数 */
    private static final int KNN_CANDIDATES = 50;

    /** 关键词召回候选数 */
    private static final int BM25_CANDIDATES = 30;

    /** RRF 融合平滑常数 */
    private static final int RRF_K = 60;

    /** Top-K 配置非法时的兜底值 */
    private static final int DEFAULT_TOP_K = 5;

    private final EmbeddingService embeddingService;
    private final RerankerService rerankerService;
    private final SearchPort searchPort;
    private final VectorSearchPort vectorSearchPort;
    private final AiProperties aiProperties;

    public RAGRetriever(EmbeddingService embeddingService, RerankerService rerankerService,
                        SearchPort searchPort, VectorSearchPort vectorSearchPort,
                        AiProperties aiProperties) {
        this.embeddingService = embeddingService;
        this.rerankerService = rerankerService;
        this.searchPort = searchPort;
        this.vectorSearchPort = vectorSearchPort;
        this.aiProperties = aiProperties;
    }

    /**
     * 限定商家知识库的检索 — 仅查询该 seller 下的 FAQ/政策/手册 chunk。
     *
     * <p><b>降级语义</b>：检索失败、向量为空或命中为空时返回 {@code null}
     * （表示「无可用上下文」），由调用方决定是否拼装 prompt —— 不使用占位文案，
     * 以免把「无知识」当成真实上下文塞进 LLM。
     *
     * @param query    用户问题
     * @param sellerId 商家ID
     * @return 上下文文本；无可用上下文时返回 {@code null}
     */
    @Nullable
    public String retrieveForSeller(String query, Long sellerId) {
        if (query == null || query.isBlank()) {
            return null;
        }
        try {
            double[] embedding = embeddingService.embed(query);
            if (embedding.length == 0) {
                return null;
            }
            List<KnowledgeHit> hits = vectorSearchPort.searchKnowledge(embedding, topK(), sellerId);
            if (hits.isEmpty()) {
                return null;
            }
            return assembleKnowledgeContext(hits);
        } catch (Exception e) {
            log.warn("Seller RAG failed (degraded to no-RAG): {}", e.getMessage());
            return null;
        }
    }

    /**
     * 检索与查询相关的上下文（完整 5 阶段管线）。
     *
     * @param query 用户问题
     * @return 上下文文本（无结果时为占位文案）
     */
    public String retrieve(String query) {
        try {
            List<ProductHit> knnResults = vectorRecall(query);
            List<ProductHit> keywordResults = keywordRecall(query);
            List<ProductHit> fused = rrfFusion(knnResults, keywordResults);
            return assembleContext(rerank(query, fused, topK()));
        } catch (Exception e) {
            log.warn("RAG retrieval failed: {}", e.getMessage());
            return "（暂无相关上下文）";
        }
    }

    /** 向量语义召回（商品索引）。 */
    private List<ProductHit> vectorRecall(String query) {
        try {
            double[] embedding = embeddingService.embed(query);
            if (embedding.length == 0) {
                return List.of();
            }
            return vectorSearchPort.vectorSearch(embedding, KNN_CANDIDATES, null);
        } catch (Exception e) {
            log.warn("Vector recall failed: {}", e.getMessage());
            return List.of();
        }
    }

    /** 关键词召回。 */
    private List<ProductHit> keywordRecall(String query) {
        try {
            return searchPort.searchProducts(query, BM25_CANDIDATES);
        } catch (Exception e) {
            log.warn("Keyword recall failed: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Reciprocal Rank Fusion (RRF) — 融合多个召回源的结果。
     * score(d) = Σ 1/(k + rank_i(d)), k=60
     */
    private List<ProductHit> rrfFusion(List<ProductHit> listA, List<ProductHit> listB) {
        Map<String, Double> scores = new LinkedHashMap<>();
        Map<String, ProductHit> docMap = new LinkedHashMap<>();
        addRRFScores(listA, scores, docMap);
        addRRFScores(listB, scores, docMap);
        return scores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .map(entry -> docMap.get(entry.getKey()))
                .filter(Objects::nonNull)
                .toList();
    }

    private void addRRFScores(List<ProductHit> docs, Map<String, Double> scores,
                              Map<String, ProductHit> docMap) {
        for (int i = 0; i < docs.size(); i++) {
            ProductHit doc = docs.get(i);
            if (doc.productId() == null) {
                continue;
            }
            scores.merge(doc.productId(), 1.0 / (RRF_K + i + 1), Double::sum);
            docMap.putIfAbsent(doc.productId(), doc);
        }
    }

    /** 重排序 — 使用 BGE-reranker（不可用时降级为启发式排序）。 */
    private List<ProductHit> rerank(String query, List<ProductHit> docs, int topK) {
        if (docs.size() <= topK) {
            return docs;
        }
        List<String> candidates = docs.stream().map(RAGRetriever::formatDocForReranking).toList();
        return rerankerService.rerank(query, candidates, topK).stream()
                .filter(index -> index >= 0 && index < docs.size())
                .map(docs::get)
                .toList();
    }

    private static String formatDocForReranking(ProductHit doc) {
        return String.format("%s %s", doc.name(), doc.description());
    }

    /** 组装商品上下文（无结果返回占位文案）。 */
    private String assembleContext(List<ProductHit> results) {
        if (results.isEmpty()) {
            return "（暂无相关上下文）";
        }
        StringBuilder sb = new StringBuilder("以下是与用户问题相关的信息：\n");
        for (int i = 0; i < results.size(); i++) {
            ProductHit item = results.get(i);
            sb.append(String.format("%d. %s (ID:%s) — ¥%.2f 销量:%s\n",
                    i + 1, item.name(), item.productId(),
                    item.priceCents() == null ? 0 : item.priceCents() / 100.0,
                    item.soldCount()));
        }
        return sb.toString();
    }

    /** 组装知识库 chunk 上下文（供客服 prompt 拼接）。 */
    private String assembleKnowledgeContext(List<KnowledgeHit> hits) {
        StringBuilder sb = new StringBuilder("以下是与用户问题相关的商家知识：\n");
        for (int i = 0; i < hits.size(); i++) {
            KnowledgeHit hit = hits.get(i);
            sb.append(String.format("%d. %s%n%s%n", i + 1,
                    hit.title() == null ? "" : hit.title(),
                    hit.text() == null ? "" : hit.text()));
        }
        return sb.toString();
    }

    /** 检索条数：取配置 {@code ai.rag.top-k}，非法值回落默认值。 */
    private int topK() {
        int configured = aiProperties.getRag().getTopK();
        return configured > 0 ? configured : DEFAULT_TOP_K;
    }
}
