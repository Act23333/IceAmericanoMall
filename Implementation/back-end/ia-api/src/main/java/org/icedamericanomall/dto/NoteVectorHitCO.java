package org.icedamericanomall.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 相似笔记命中（search-service → item-service，契约 §4.14.10 I51，V5.13）。
 *
 * <p>只出 {@code noteId + score}——笔记卡（标题/封面/点赞数/发布态过滤）由 item 侧
 * 查本地库组装（MySQL 是展示真相源，ADR-046 同口径）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NoteVectorHitCO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 命中笔记 id（已排除自身） */
    private Long noteId;

    /** 向量相似度得分（ES kNN _score，越高越相似） */
    private double score;
}
