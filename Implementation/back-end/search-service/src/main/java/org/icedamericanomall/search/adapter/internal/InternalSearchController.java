package org.icedamericanomall.search.adapter.internal;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.client.KnowledgeVectorClient;
import org.icedamericanomall.search.adapter.assembler.ProductSearchConverter;
import org.icedamericanomall.search.adapter.request.KnowledgeVectorUpsertReq;
import org.icedamericanomall.search.adapter.request.VectorSearchReq;
import org.icedamericanomall.search.adapter.response.KnowledgeChunkHitVO;
import org.icedamericanomall.search.app.KnowledgeVectorManager;
import org.icedamericanomall.search.app.SearchManager;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 内部搜索管理接口 — 索引重建 / 向量检索 / 知识库向量摄取。
 *
 * <p>内部接口（{@code /internal/**}）按约定不包装 {@code Result}，异常原样透出。
 * Controller 仅做参数解析 + 委托 app 层（{@link SearchManager} / {@link KnowledgeVectorManager}）。
 *
 * <p><b>向量索引归属</b>：{@code knowledge_vectors} 索引（含分块与 Embedding 调用）归本服务所有；
 * ai-service 通过 {@link KnowledgeVectorClient} 提交<b>原文</b>并取回 chunk 文本，不感知维度与切片策略。
 *
 * <p><b>渐进启用 / 降级</b>：ES 关闭（{@code search.elasticsearch.enabled=false}）时
 * {@link KnowledgeVectorManager} 不存在，知识库向量端点按「未启用」处理（摄取返回 0、删除返回 false、
 * 检索返回空），<b>不抛异常</b>，从而不阻断 ai-service 的知识库 CRUD。
 */
@Slf4j
@RestController
@RequestMapping("/internal/search")
@RequiredArgsConstructor
public class InternalSearchController {

    private final SearchManager searchManager;
    private final ProductSearchConverter converter;

    /** ES 关闭时无此 Bean，故用 ObjectProvider 软依赖（不阻断启动，按未启用处理）。 */
    private final ObjectProvider<KnowledgeVectorManager> knowledgeVectorManager;

    @PostMapping("/reindex")
    public Map<String, Object> reindex() {
        return searchManager.reindex();
    }

    /**
     * 向量 kNN 检索：{@code index=products}（缺省，历史行为）走商品索引；
     * {@code index=knowledge_vectors} 走知识库 chunk 索引（支持 sellerId 过滤）。
     */
    @PostMapping("/vector")
    public List<?> vectorSearch(@RequestBody VectorSearchReq req) {
        if (KnowledgeVectorClient.INDEX_KNOWLEDGE_VECTORS.equals(req.normalizedIndex())) {
            return searchKnowledgeChunks(req);
        }
        List<Double> embedding = req.embedding();
        if (embedding == null || embedding.isEmpty()) {
            return List.of();
        }
        double[] vector = embedding.stream().mapToDouble(Double::doubleValue).toArray();
        return converter.hitsToVOs(searchManager.vectorSearch(vector, req.size()));
    }

    /**
     * 知识库向量摄取 —— 服务端分块 + 向量化 + 幂等重写。
     *
     * @return 写入 chunk 数；Embedding/ES 未启用返回 0（不报错）
     */
    @PostMapping("/vector/knowledge/upsert")
    public int upsertKnowledgeVector(@RequestBody @Valid KnowledgeVectorUpsertReq req) {
        KnowledgeVectorManager manager = knowledgeVectorManager.getIfAvailable();
        if (manager == null) {
            log.warn("知识库向量摄取未启用（ES 关闭），跳过: knowledgeId={}", req.knowledgeId());
            return 0;
        }
        return manager.upsert(req.knowledgeId(), req.sellerId(), req.title(), req.content());
    }

    /**
     * 按 knowledgeId 删除知识库向量 chunk。
     *
     * @return 是否处理；ES 未启用返回 false
     */
    @PostMapping("/vector/knowledge/delete")
    public boolean deleteKnowledgeVector(@RequestParam("knowledgeId") Long knowledgeId) {
        KnowledgeVectorManager manager = knowledgeVectorManager.getIfAvailable();
        if (manager == null) {
            log.warn("知识库向量删除未启用（ES 关闭），跳过: knowledgeId={}", knowledgeId);
            return false;
        }
        manager.delete(knowledgeId);
        return true;
    }

    private List<KnowledgeChunkHitVO> searchKnowledgeChunks(VectorSearchReq req) {
        KnowledgeVectorManager manager = knowledgeVectorManager.getIfAvailable();
        if (manager == null) {
            log.warn("知识库向量检索未启用（ES 关闭），按无命中处理");
            return List.of();
        }
        return manager.searchKnowledge(req.embedding(), req.size(), req.sellerId()).stream()
                .map(KnowledgeChunkHitVO::from)
                .toList();
    }
}
