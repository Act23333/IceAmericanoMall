package org.icedamericanomall.search.adapter.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.io.Serializable;

/**
 * 笔记向量摄取入参（内部端点 {@code POST /internal/search/vector/note/upsert}，契约 I50，V5.13）。
 *
 * <p>字段与 ia-api {@code NoteVectorUpsertCO} 一一对齐：只收笔记原文，
 * 向量在 search-service 内完成（单向量不分块，ADR-046），调用方不传向量。
 * {@code deleted=true} 为软删/取消发布语义：删除该笔记向量文档，其余字段忽略。
 *
 * @param noteId  笔记 id（幂等键）
 * @param title   笔记标题（向量化文本主来源）
 * @param content 笔记正文（向量化文本，契约 ≤2000 字符）
 * @param deleted 删除标记：true = 从向量索引移除该笔记
 */
public record NoteVectorUpsertReq(
        @NotNull(message = "noteId 不能为空") Long noteId,
        @NotBlank(message = "title 不能为空") String title,
        @Size(max = 2000, message = "content 不能超过 2000 字符") String content,
        boolean deleted
) implements Serializable {
}
