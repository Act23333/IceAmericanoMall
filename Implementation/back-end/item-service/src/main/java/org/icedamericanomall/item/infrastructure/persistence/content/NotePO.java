package org.icedamericanomall.item.infrastructure.persistence.content;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 种草笔记表映射对象（{@code note}）—— Infrastructure 独占，与领域聚合
 * {@code Note} 的互转内联在 {@code NoteRepositoryImpl}。
 *
 * <p>表结构（ADR-048 直入 Initialize.sql 快照）：{@code images} 为 JSON 数组字符串列
 * （≤9 个 URL，不引入 TypeHandler）；{@code like_count} 冗余计数列由 Mapper 单条 SQL 原子维护；
 * {@code status} 1=已发布 2=已删除（软删）。
 */
@Data
@TableName("note")
public class NotePO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 作者 user_id。 */
    private Long userId;

    private String title;

    private String content;

    /** 封面图。 */
    private String coverImage;

    /** 图集 JSON 数组字符串（≤9 个 URL；与聚合 {@code List<String>} 互转在仓储实现）。 */
    private String images;

    /** 1=已发布 2=已删除（软删）。 */
    private Integer status;

    /** 点赞数（冗余计数，原子维护）。 */
    private Integer likeCount;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
