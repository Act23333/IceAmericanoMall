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
 * 笔记点赞表映射对象（{@code note_like}）—— Note 聚合内部关联表，
 * {@code uk_note_user} 保证点赞幂等（重复插入由数据库唯一键拒绝）。
 */
@Data
@TableName("note_like")
public class NoteLikePO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 笔记 id。 */
    private Long noteId;

    /** 点赞用户 user_id。 */
    private Long userId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
