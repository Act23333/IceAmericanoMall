package org.icedamericanomall.trade.adapter.response;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 发票视图对象（INV1-INV5 / AD57-60 共用，契约 §4.15 VO 定稿口径）。
 *
 * <p><b>PII 红线</b>：税号/邮箱只回掩码（{@code taxNoMask}/{@code emailMask}），
 * <b>禁止</b>出现 cipher/hash 等技术字段（09-Constraints VO 约束）。
 * {@code pdfKey} 字段承载<b>已开具</b>发票样票的预签名下载地址（待开具/终态为 null）。
 * 在契约字段之外补充 {@code userId}/{@code sellerId}（管理端列表识别买家与开票主体所需，
 * 新增字段向后兼容；仍不含任何技术字段）。
 */
@Data
public class InvoiceVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 发票申请号（业务唯一键） */
    private String invoiceNo;

    /** 关联订单号 */
    private String orderNo;

    /** 买家 user_id（管理端列表识别申请人） */
    private Long userId;

    /** 开票主体商家 user_id（管理端列表/筛选展示） */
    private Long sellerId;

    /** 抬头类型（1=个人 2=企业） */
    private Integer type;

    /** 抬头快照（个人=姓名；企业=企业名称） */
    private String title;

    /** 企业税号掩码展示（个人抬头为 null） */
    private String taxNoMask;

    /** 交付邮箱掩码展示（未填为 null） */
    private String emailMask;

    /** 开票金额（分）= payAmount 快照 */
    private Integer amount;

    /** 状态（1 待开具 2 已开具 3 已冲红 4 已作废） */
    private Integer status;

    /** 开具时间（已开具才有） */
    private LocalDateTime issueTime;

    /** 部分退款待人工处理标记（0/1） */
    private Integer partialRefundFlag;

    /** 样票预签名下载地址（仅已开具；待开具/终态为 null） */
    private String pdfKey;

    /** 申请时间 */
    private LocalDateTime createTime;
}
