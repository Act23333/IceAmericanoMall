package org.icedamericanomall.ai.domain.model.knowledge;

/**
 * 知识条目待摄取内容（领域 DTO）—— 提交给向量索引所有方（search-service）。
 *
 * <p>只含原文：分块（chunking）与向量化（embedding）都由 search-service 完成，
 * 本上下文不感知 chunk 边界与向量维度（避免两处模型/维度口径漂移）。
 *
 * @param knowledgeId 知识条目业务ID（幂等键）
 * @param sellerId    归属商家ID
 * @param title       条目标题
 * @param content     条目正文
 */
public record KnowledgeVectorDoc(
        Long knowledgeId,
        Long sellerId,
        String title,
        String content
) {
}
