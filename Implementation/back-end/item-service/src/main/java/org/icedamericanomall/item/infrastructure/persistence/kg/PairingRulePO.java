package org.icedamericanomall.item.infrastructure.persistence.kg;

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
 * 类目搭配规则 PO（{@code pairing_rule} 表映射，Infrastructure 独占；
 * DDL 见 {@code database/Initialize.sql} V5.16 段）。
 */
@Data
@TableName("pairing_rule")
public class PairingRulePO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 源类目 ID（uk_src_dst 左半）。 */
    private Long srcCategoryId;

    /** 目标类目 ID（uk_src_dst 右半）。 */
    private Long dstCategoryId;

    /** 排序权重（越大越靠前）。 */
    private Integer weight;

    /** 备注。 */
    private String remark;

    /** 1=启用 0=停用（停用即时生效于邻居查询 status 过滤）。 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
