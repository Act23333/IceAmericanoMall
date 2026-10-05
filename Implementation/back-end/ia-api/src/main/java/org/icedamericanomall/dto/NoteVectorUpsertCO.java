package org.icedamericanomall.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 笔记向量摄取入参（item-service → search-service，契约 §4.14.10 I50，V5.13）。
 *
 * <p>幂等覆盖写：同 {@code noteId} 重写整份笔记向量文档（ADR-046 单向量口径，文本源=title+content）；
 * 软删/取消发布由调用方以 {@code deleted=true} 语义触发删除该向量文档。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NoteVectorUpsertCO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 笔记 id（幂等键） */
    @NotNull
    private Long noteId;

    /** 笔记标题（向量化文本主来源） */
    @NotBlank
    private String title;

    /** 笔记正文（向量化文本） */
    private String content;

    /** 删除标记：true = 从向量索引移除该笔记（软删/下架场景），其余字段忽略 */
    private boolean deleted;
}
