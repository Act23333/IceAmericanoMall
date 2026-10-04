package org.icedamericanomall.search.domain.gateway;

import org.icedamericanomall.search.domain.model.search.KnowledgeChunkHit;
import org.icedamericanomall.search.domain.service.DocumentChunker;

import java.util.List;

/**
 * 知识库向量索引网关（领域端口）—— {@code knowledge_vectors} 索引的建/删/写/检索抽象。
 *
 * <p>实现方：{@code infrastructure.es.KnowledgeVectorIndexAdapter}（低层 ElasticsearchClient）。
 * 幂等口径：写入前调用方先 {@link #deleteByKnowledgeId(Long)}，doc _id 固定 {@code {knowledgeId}_{chunkIndex}}。
 */
public interface KnowledgeVectorIndexGateway {

    /** 确保索引存在（不存在则按配置 dims 创建；并发已建则静默通过）。 */
    void ensureIndex();

    /** 按 knowledgeId 删除该条目全部 chunk。 */
    void deleteByKnowledgeId(Long knowledgeId);

    /**
     * 批量写入 chunk（每 chunk 一条 doc）。
     *
     * @param knowledgeId 知识条目业务ID
     * @param sellerId    归属商家ID
     * @param chunks      分块结果
     * @param vectors     与 chunks 一一对应的向量
     */
    void indexChunks(Long knowledgeId, Long sellerId,
                     List<DocumentChunker.Chunk> chunks, List<float[]> vectors);

    /**
     * kNN 检索知识 chunk（可按 sellerId 过滤）。
     *
     * @param queryVector 查询向量
     * @param size        Top-K（≤0 用配置缺省）
     * @param sellerId    商家过滤（null = 全站）
     * @return 命中列表（无结果/索引不存在为空列表）
     */
    List<KnowledgeChunkHit> knnSearch(float[] queryVector, int size, Long sellerId);
}
