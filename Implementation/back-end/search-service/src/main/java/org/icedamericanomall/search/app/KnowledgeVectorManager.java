package org.icedamericanomall.search.app;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BadRequestException;
import org.icedamericanomall.search.domain.gateway.EmbeddingGateway;
import org.icedamericanomall.search.domain.gateway.KnowledgeVectorIndexGateway;
import org.icedamericanomall.search.domain.model.search.KnowledgeChunkHit;
import org.icedamericanomall.search.domain.service.DocumentChunker;
import org.icedamericanomall.search.infrastructure.config.KnowledgeVectorProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 知识库向量编排（应用层）—— 摄取 / 删除 / 检索（向量索引归 search-service 所有）。
 *
 * <p><b>摄取链路</b>：原文 → 分块（{@link DocumentChunker}，领域服务）→ 逐块向量化
 * （{@link EmbeddingGateway}）→ 先删后写 {@code knowledge_vectors}（{@link KnowledgeVectorIndexGateway}）。
 * 分块与向量化都在本服务，ai-service 只提交原文、只取回 chunk 文本。
 *
 * <p><b>渐进启用 / 全链路降级</b>：
 * <ul>
 *   <li>Embedding 未配置 → 摄取返回 0 并记 WARN，<b>不报错</b>（ES/Embedding 就绪前的默认状态）；</li>
 *   <li>Embedding 调用失败 → 抛 BizException，由调用方（ai-service）捕获并降级，不影响知识库 MySQL 主流程；</li>
 *   <li>检索失败 → 网关实现返回空列表（等价「无命中」），客服侧直接无 RAG 应答。</li>
 * </ul>
 *
 * <p>仅 {@code search.elasticsearch.enabled=true} 时装配（依赖 ES 网关实现）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "search.elasticsearch.enabled", havingValue = "true")
public class KnowledgeVectorManager {

    private final EmbeddingGateway embeddingGateway;
    private final KnowledgeVectorIndexGateway indexGateway;
    private final DocumentChunker documentChunker;
    private final KnowledgeVectorProperties indexProperties;

    /**
     * 摄取知识条目：分块 + 向量化 + 幂等重写索引。
     *
     * @param knowledgeId 知识条目业务ID（幂等键，必填）
     * @param sellerId    归属商家ID
     * @param title       条目标题
     * @param content     条目正文
     * @return 写入的 chunk 数；Embedding 未配置时返回 0（未启用，不报错）
     * @throws BadRequestException knowledgeId 为空
     * @throws org.icedamericanomall.common.exception.BizException 向量化或写入失败（调用方降级）
     */
    public int upsert(Long knowledgeId, Long sellerId, String title, String content) {
        if (knowledgeId == null) {
            throw new BadRequestException(ErrorCode.PARAM_ERROR, "knowledgeId 不能为空");
        }
        if (!embeddingGateway.isConfigured()) {
            log.warn("知识库向量摄取未启用（search.embedding 未配置），跳过: knowledgeId={}", knowledgeId);
            return 0;
        }
        List<DocumentChunker.Chunk> chunks = documentChunker.chunkAuto(title, content);
        if (chunks.size() > indexProperties.getMaxChunks()) {
            log.warn("知识库条目 chunk 数超上限({}), 截断: knowledgeId={}, chunks={}",
                    indexProperties.getMaxChunks(), knowledgeId, chunks.size());
            chunks = chunks.subList(0, indexProperties.getMaxChunks());
        }
        List<float[]> vectors = new ArrayList<>(chunks.size());
        for (DocumentChunker.Chunk chunk : chunks) {
            vectors.add(embeddingGateway.embed(chunk.text()));
        }
        indexGateway.ensureIndex();
        indexGateway.deleteByKnowledgeId(knowledgeId);
        indexGateway.indexChunks(knowledgeId, sellerId, chunks, vectors);
        return chunks.size();
    }

    /**
     * 删除知识条目在向量索引中的全部 chunk（knowledgeId 为空时直接返回）。
     */
    public void delete(Long knowledgeId) {
        if (knowledgeId == null) {
            return;
        }
        indexGateway.ensureIndex();
        indexGateway.deleteByKnowledgeId(knowledgeId);
    }

    /**
     * 检索知识 chunk（按 sellerId 过滤，商家隔离）。
     *
     * @param embedding 查询向量（空则返回空列表，不发起 ES 调用）
     * @param size      Top-K（≤0 用配置缺省）
     * @param sellerId  商家过滤（null = 全站）
     * @return 命中列表（无结果为空列表）
     */
    public List<KnowledgeChunkHit> searchKnowledge(List<Double> embedding, int size, Long sellerId) {
        if (embedding == null || embedding.isEmpty()) {
            return List.of();
        }
        float[] vector = new float[embedding.size()];
        for (int i = 0; i < embedding.size(); i++) {
            vector[i] = embedding.get(i).floatValue();
        }
        return indexGateway.knnSearch(vector, size, sellerId);
    }
}
