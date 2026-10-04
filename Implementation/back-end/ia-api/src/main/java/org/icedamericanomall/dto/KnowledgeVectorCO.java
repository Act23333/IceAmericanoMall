package org.icedamericanomall.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 知识库向量摄取请求（跨服务契约对象，ai-service → search-service）。
 *
 * <p>对应内部端点 {@code POST /internal/search/vector/knowledge/upsert}。
 * <b>只传原文，不传分块与向量</b>：分块（chunking）与向量化（embedding）都归
 * search-service（ES 域所有方）完成 —— 索引映射、维度、切片策略属于向量库内部细节，
 * 调用方（ai-service）不应感知，避免两处维度/模型口径漂移。
 *
 * <p>幂等口径：服务端按 {@code knowledgeId} 先删后写，重复提交同一 {@code knowledgeId}
 * 不会产生重复 chunk。
 */
@Data
public class KnowledgeVectorCO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 知识条目业务ID（{@code merchant_knowledge_base.id}），幂等键。 */
    private Long knowledgeId;

    /** 归属商家ID（写入 doc 供检索侧过滤，商家隔离的唯一依据）。 */
    private Long sellerId;

    /** 条目标题（chunk 标题前缀，也作为检索命中的可读标题）。 */
    private String title;

    /** 条目正文（服务端按标题+正文分块后逐块向量化）。 */
    private String content;
}
