package org.icedamericanomall.client;

import org.icedamericanomall.config.DefaultFeignConfig;
import org.icedamericanomall.dto.KnowledgeVectorCO;
import org.icedamericanomall.dto.KnowledgeVectorSearchCO;
import org.icedamericanomall.fallback.KnowledgeVectorClientFallback;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

/**
 * Feign client for search-service internal knowledge-vector endpoints (the sole vector-index owner).
 *
 * <p><b>职责边界</b>：向量索引（ES 域）归 search-service 所有 —— 分块、Embedding 调用、
 * 索引 mapping、维度都由它对内自治；ai-service 只做编排（提交原文 / 删除 / 取回 chunk 文本），
 * 不感知 dims 与切片策略，避免两处 Embedding 模型口径漂移。
 *
 * <p><b>降级语义：fail-open（不抛死）</b>。知识库摄取/检索都是对话链路的<b>增强能力</b>：
 * <ul>
 *   <li>{@link #upsertKnowledgeVectors} 降级返回 {@code -1}（写入 chunk 数的哨兵值），
 *       调用方据此认为「本次未建立索引」，但知识条目本身已在 MySQL 落库，主流程不受影响；</li>
 *   <li>{@link #deleteKnowledgeVectors} 降级返回 {@code Boolean.FALSE}；</li>
 *   <li>查询降级返回空列表 —— 与「检索无结果」同义，调用方直接无 RAG 应答。
 * </ul>
 * 之所以不用 fail-closed 抛异常：知识库 CRUD 与客服应答都不能因为向量库/Embedding 不可用而失败
 * （见 {@link KnowledgeVectorClientFallback}）。
 *
 * <p><b>契约形状</b>：查询返回 {@code List<Map>} 是<b>双形状弱类型契约</b>——
 * 同一端点按 {@code index} 返回商品命中（{@code productId/name/description/price/soldCount}）
 * 或知识 chunk 命中（{@code knowledgeId/chunkIndex/sellerId/title/text}）。
 * 这与 {@link SearchClient} 的既有口径一致，调用方必须做 null/缺键防御。
 *
 * <p>⚠️ 生效前提：调用方需开启 {@code spring.cloud.openfeign.circuitbreaker.enabled=true}，
 * 且降级工厂已在 {@link DefaultFeignConfig} 显式登记。
 */
@FeignClient(
    name = "search-service",
    path = "/internal/search",
    contextId = "knowledgeVector",
    configuration = DefaultFeignConfig.class,
    fallbackFactory = KnowledgeVectorClientFallback.class
)
public interface KnowledgeVectorClient {

    /** 商品索引名（历史默认）。 */
    String INDEX_PRODUCTS = "products";

    /** 商家知识库 chunk 索引名。 */
    String INDEX_KNOWLEDGE_VECTORS = "knowledge_vectors";

    /**
     * 知识库向量摄取（服务端分块 + 向量化 + 先删后写，幂等）。
     *
     * @param co 知识条目原文（knowledgeId 为幂等键）
     * @return 实际写入的 chunk 数；降级为 {@code -1}
     */
    @PostMapping("/vector/knowledge/upsert")
    Integer upsertKnowledgeVectors(@RequestBody KnowledgeVectorCO co);

    /**
     * 按 knowledgeId 删除该条目在向量索引中的全部 chunk。
     *
     * @param knowledgeId 知识条目业务ID
     * @return 是否成功；降级为 {@code false}
     */
    @PostMapping("/vector/knowledge/delete")
    Boolean deleteKnowledgeVectors(@RequestParam("knowledgeId") Long knowledgeId);

    /**
     * 向量 kNN 检索（商品 / 知识 chunk 由 {@code index} 区分）。
     *
     * @param co 查询向量 + Top-K + 可选 sellerId 过滤 + 目标索引
     * @return 命中列表原始字段 Map；无结果或降级为空列表
     */
    @PostMapping("/vector")
    List<Map<String, Object>> vectorSearch(@RequestBody KnowledgeVectorSearchCO co);
}
