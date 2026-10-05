package org.icedamericanomall.trade.infrastructure.persistence.invoice;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * {@code invoice_title} 表映射对象（Infrastructure 独占）—— 常用发票抬头。
 *
 * <p>{@code is_default} 为 NULL-唯一语义列（{@code uk_user_default} 多 NULL → 一用户至多一条默认）；
 * {@code deleted} 软删标记（{@code @TableLogic}，可恢复）。
 */
@Data
@TableName("invoice_title")
public class InvoiceTitlePO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属用户 user_id */
    private Long userId;

    /** 抬头类型：1=PERSONAL 2=COMPANY */
    private Integer type;

    /** 抬头 */
    private String title;

    /** 企业税号密文（PII 三字段） */
    private String taxNoCipher;

    /** 企业税号盲索引 */
    private String taxNoHash;

    /** 企业税号脱敏展示 */
    private String taxNoMask;

    /** 是否默认抬头：1=默认；NULL=非默认 */
    private Integer isDefault;

    /** 软删标记（@TableLogic） */
    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
