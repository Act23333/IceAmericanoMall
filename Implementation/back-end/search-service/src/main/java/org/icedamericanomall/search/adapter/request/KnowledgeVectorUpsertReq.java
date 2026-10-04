package org.icedamericanomall.search.adapter.request;

import jakarta.validation.constraints.NotNull;

import java.io.Serializable;

/**
 * 知识库向量摄取入参（内部端点 {@code POST /internal/search/vector/knowledge/upsert}）。
 *
 * <p>只收原文：分块与向量化在 search-service 内完成（向量索引所有方），调用方不传 chunks/vector。
 *
 * @param knowledgeId 知识条目业务ID（幂等键）
 * @param sellerId    归属商家ID（写入 doc 供检索过滤）
 * @param title       条目标题
 * @param content     条目正文
 */
public record KnowledgeVectorUpsertReq(
        @NotNull(message = "knowledgeId 不能为空") Long knowledgeId,
        Long sellerId,
        String title,
        String content
) implements Serializable {
}
