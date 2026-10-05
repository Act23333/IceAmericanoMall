package org.icedamericanomall.trade.domain.model.invoice;

import lombok.Data;
import org.icedamericanomall.common.model.EncryptedIdentifier;

import java.time.LocalDateTime;

/**
 * 常用发票抬头聚合（纯 POJO）—— {@code invoice_title} 表（PRD §15.4.2，决策 O2 随批简版）。
 *
 * <p>定位：仅服务申请体验（INV1 {@code titleId} 引用），发票单持有的是<b>申请时快照</b>，
 * 本表增删改不影响已申请发票。默认抬头唯一性由 {@code uk_user_default(user_id, is_default)}
 * 多 NULL 特性兜底（一用户至多一条默认），换设默认前须先清旧默认（仓储
 * {@code clearDefaultFlag} 语义）。软删（{@code deleted} {@code @TableLogic}，可恢复）。
 */
@Data
public class InvoiceTitle {

    private Long id;

    /** 所属用户 user_id */
    private Long userId;

    /** 抬头类型（1=个人 2=企业） */
    private Integer type;

    /** 抬头（个人=姓名；企业=企业名称） */
    private String title;

    /** 企业税号（PII 三字段；个人抬头为 null） */
    private EncryptedIdentifier taxNo;

    /** 是否默认抬头（1=默认；null=非默认，uk_user_default 多 NULL 语义） */
    private Integer isDefault;

    /** 软删标记（@TableLogic，持久化层承载） */
    private Integer deleted;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
