package org.icedamericanomall.search.domain.model.search;

/**
 * 知识库向量检索命中（领域读模型）—— {@code knowledge_vectors} 索引 source 白名单字段。
 *
 * @param knowledgeId 知识条目业务ID
 * @param chunkIndex  chunk 序号
 * @param sellerId    归属商家ID
 * @param title       chunk 标题
 * @param text        chunk 正文
 */
public record KnowledgeChunkHit(Long knowledgeId, Integer chunkIndex, Long sellerId,
                                String title, String text) {
}
