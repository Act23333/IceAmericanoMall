package org.icedamericanomall.ai.domain.port;

import org.icedamericanomall.ai.domain.model.knowledge.KnowledgeVectorDoc;

/**
 * 知识库向量摄取端口（领域层定义，基础设施层用 Feign 实现）—— 写侧的防腐层。
 *
 * <p>职责边界：向量索引与向量化都归 search-service（ES 域所有方），本端口只表达
 * 「把这个知识条目同步到向量索引 / 从向量索引移除」这一业务意图，不暴露 chunk 与向量细节。
 *
 * <p><b>失败语义：fail-open（不抛异常，返回失败哨兵）</b>。理由：摄取是知识库 CRUD 的
 * <b>旁路增强</b> —— 条目已在 MySQL 落库，索引失败不能让保存失败。因此：
 * <ul>
 *   <li>{@link #upsertKnowledgeVector} 返回 {@code -1} 表示本次未建立索引（调用方<b>不得</b>据此写入 embeddingId）；</li>
 *   <li>{@link #deleteKnowledgeVector} 返回 {@code false} 表示未清理索引；</li>
 *   <li>调用方仍应在外层做 try/catch + WARN 兜底（双保险，见 KnowledgeBaseManager）。</li>
 * </ul>
 */
public interface KnowledgeVectorPort {

    /** 摄取失败哨兵：未写入任何 chunk。 */
    int UPSERT_FAILED = -1;

    /**
     * 同步（幂等重写）知识条目的向量索引。
     *
     * @param doc 条目原文（knowledgeId 为幂等键）
     * @return 写入的 chunk 数；{@link #UPSERT_FAILED} = 失败/未启用（哨兵值）
     */
    int upsertKnowledgeVector(KnowledgeVectorDoc doc);

    /**
     * 从向量索引移除该条目的全部 chunk。
     *
     * @param knowledgeId 知识条目业务ID
     * @return true = 已清理；false = 失败/未启用
     */
    boolean deleteKnowledgeVector(Long knowledgeId);
}
