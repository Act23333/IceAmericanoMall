package org.icedamericanomall.trade.adapter.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 常用发票抬头入参（INV7 新建 / INV8 编辑共用，契约 §4.15 V5.16.1）。
 *
 * <p>企业抬头税号必填为<b>服务端条件校验</b>（聚合自封装守卫，PARAM_ERROR）；
 * 税号 PII 三字段落库（cipher + hash + mask，O4 从严），报文只收明文、出参只回掩码。
 * INV8 为全量更新语义：type/title/taxNo/isDefault 整体替换。
 */
@Data
public class InvoiceTitleReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 抬头类型（必填，1=个人 2=企业）。 */
    @NotNull(message = "抬头类型不能为空")
    private Integer type;

    /** 抬头（必填；个人=姓名，企业=企业名称）。 */
    @NotBlank(message = "发票抬头不能为空")
    @Size(max = 100, message = "发票抬头超长")
    private String title;

    /** 企业税号（企业抬头必填，服务端条件校验；个人抬头忽略）。 */
    @Size(max = 64, message = "税号超长")
    private String taxNo;

    /** 是否设为默认抬头（缺省 false；true 时服务端清除本人旧默认）。 */
    private Boolean isDefault;
}
