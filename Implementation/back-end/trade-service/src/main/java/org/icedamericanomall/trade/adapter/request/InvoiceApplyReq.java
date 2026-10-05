package org.icedamericanomall.trade.adapter.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 发票申请入参（INV1 {@code POST /api/invoice/apply}，契约 §4.15）。
 *
 * <p>两种抬头来源（决策 O3）：
 * <ul>
 *   <li>{@code titleId} 引用常用抬头 —— 类型/抬头/税号均以抬头表快照为准（报文其余抬头字段忽略）；</li>
 *   <li>现填 —— {@code type} 必填、{@code title} 必填、企业 {@code taxNo} 必填（服务端条件校验）。</li>
 * </ul>
 * 税号/邮箱为 PII 三字段落库（cipher + hash + mask，O4 从严），报文只收明文、出参只回掩码。
 */
@Data
public class InvoiceApplyReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 关联订单号（必填）。 */
    @NotBlank(message = "订单号不能为空")
    private String orderNo;

    /** 抬头类型（必填，1=个人 2=企业）。 */
    @NotNull(message = "抬头类型不能为空")
    private Integer type;

    /** 常用抬头 id（可选引用；引用时现填字段忽略）。 */
    private Long titleId;

    /** 抬头（现填时必填；个人=姓名，企业=企业名称）。 */
    @Size(max = 100, message = "发票抬头超长")
    private String title;

    /** 企业税号（企业抬头必填，服务端条件校验）。 */
    @Size(max = 64, message = "税号超长")
    private String taxNo;

    /** 交付邮箱（可选）。 */
    @Email(message = "邮箱格式非法")
    @Size(max = 100, message = "邮箱超长")
    private String email;
}
