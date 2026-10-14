package org.icedamericanomall.trade.infrastructure.persistence.invoice;

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
 * {@code invoice} 表映射对象（Infrastructure 独占）—— 发票单（F14，PRD §十五）。
 *
 * <p>税号/邮箱为 PII 三字段列（cipher + hash + mask，O4 从严）；领域侧聚合持有
 * {@code EncryptedIdentifier}，转换在 RepositoryImpl 内联完成。
 */
@Data
@TableName("invoice")
public class InvoicePO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 发票申请号（业务唯一键，uk_invoice_no） */
    /** 发票申请号（业务唯一键，uk_invoice_no；BINARY(16) ↔ byte[]，ADR-050） */
    private byte[] invoiceNo;

    /** 关联订单号 */
    /** 关联订单号（BINARY(16) ↔ byte[]；hex 形态仅在领域层，ADR-050） */
    private byte[] orderNo;

    /** 有效票标记：1=有效；NULL=终态（uk_order_active 多 NULL 语义） */
    private Integer activeFlag;

    /** 买家 user_id */
    private Long userId;

    /** 开票主体商家 user_id */
    private Long sellerId;

    /** 抬头类型：1=PERSONAL 2=COMPANY */
    private Integer type;

    /** 抬头快照 */
    private String title;

    /** 企业税号密文（AES-256-GCM） */
    private String taxNoCipher;

    /** 企业税号盲索引（HMAC-SHA256） */
    private String taxNoHash;

    /** 企业税号脱敏展示 */
    private String taxNoMask;

    /** 交付邮箱密文（AES-256-GCM） */
    private String emailCipher;

    /** 交付邮箱盲索引（HMAC-SHA256） */
    private String emailHash;

    /** 交付邮箱脱敏展示 */
    private String emailMask;

    /** 开票金额（分）= payAmount 快照 */
    private Integer amount;

    /** 状态：1=待开具 2=已开具 3=已冲红 4=已作废 */
    private Integer status;

    /** 开具时间 */
    private LocalDateTime issueTime;

    /** 模拟票号 */
    private String mockInvoiceNo;

    /** 样票 MinIO objectKey */
    private String pdfKey;

    /** 冲红关联：换开新票指向被冲红/终态原票 id */
    private Long redForId;

    /** 冲红/作废原因 */
    private String redReason;

    /** 部分退款待人工处理标记（0/1） */
    private Integer partialRefundFlag;

    /** 最近催开时间 */
    private LocalDateTime urgeTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
