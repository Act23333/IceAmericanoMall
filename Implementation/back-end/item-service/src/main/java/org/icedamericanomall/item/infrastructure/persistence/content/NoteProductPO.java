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
 * 笔记关联商品表映射对象（{@code note_product}）—— Note 聚合内部关联表，
 * ≤5 条（{@code uk_note_product}），sort 记录发布时的关联展示顺序。
 */
@Data
@TableName("note_product")
public class NoteProductPO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 笔记 id。 */
    private Long noteId;

    /** 关联商品 id。 */
    private Long productId;

    /** 展示顺序（0 起）。 */
    private Integer sort;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
