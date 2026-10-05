package org.icedamericanomall.trade.adapter.response;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 常用发票抬头视图对象（INV6-INV9，契约 §4.15 V5.16.1）。
 *
 * <p><b>PII 红线</b>：税号只回掩码（{@code taxNoMask}），<b>禁止</b>出现
 * cipher/hash 等技术字段（09-Constraints VO 约束）。
 */
@Data
public class InvoiceTitleVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 抬头 id（INV1 {@code titleId} 引用入口） */
    private Long id;

    /** 抬头类型（1=个人 2=企业） */
    private Integer type;

    /** 抬头（个人=姓名；企业=企业名称） */
    private String title;

    /** 企业税号掩码展示（个人抬头为 null） */
    private String taxNoMask;

    /** 是否默认抬头（1=默认 0=非默认，NULL 归一输出为 0） */
    private Integer isDefault;

    /** 创建时间 */
    private LocalDateTime createTime;
}
