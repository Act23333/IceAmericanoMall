package org.icedamericanomall.ai.domain.model.knowledge;

/**
 * 知识库检索命中项（领域 DTO）—— 商家 FAQ/政策/手册的 chunk。
 *
 * <p>与 {@link org.icedamericanomall.ai.domain.model.spu.SpuHit} 区分：知识命中携带的是<b>可读文本片段</b>（送入 LLM 的上下文），
 * 而不是商品价格/销量等结构化字段。
 *
 * @param knowledgeId 知识条目业务ID
 * @param chunkIndex  条目内 chunk 序号
 * @param title       chunk 标题
 * @param text        chunk 正文（上下文片段）
 */
public record KnowledgeHit(
        Long knowledgeId,
        Integer chunkIndex,
        String title,
        String text
) {
}
