package org.icedamericanomall.item.adapter.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 运费模板保存请求（F2 运费模型，契约 {@code 05-API-Specification.md} §4.4.9）。
 *
 * <p>用于 {@code POST /api/item/freight-template}（新建）与
 * {@code PUT /api/item/freight-template/{id}}（编辑）。
 * {@code sellerId} 由登录态（{@code UserContext}）注入，请求体不承载商家身份。
 *
 * <p><b>字段缺省口径</b>：{@code chargeMode} 缺省 1（固定运费，Phase 1 唯一值）；
 * {@code freeThreshold} 缺省 0（不参与满额包邮）；{@code status} 缺省 1（启用）。
 * 编辑（PUT）为<b>局部更新</b>语义：为 {@code null} 的字段保持不变。
 */
@Data
public class FreightTemplateSaveReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 模板名称（必填，≤50 字符，同商家内唯一）。 */
    @NotBlank(message = "模板名称不能为空")
    @Size(max = 50, message = "模板名称长度不能超过 50")
    private String templateName;

    /** 计费方式：缺省 1-固定运费（Phase 1 唯一值）。 */
    private Integer chargeMode;

    /** 固定运费（分，必填且 ≥0）。 */
    @NotNull(message = "运费金额不能为空")
    @Min(value = 0, message = "运费金额不能为负")
    private Integer fee;

    /** 满额包邮阈值（分）：缺省 0 = 不参与满额包邮；>0 时订单商品总额达标则运费为 0。 */
    @Min(value = 0, message = "满额包邮阈值不能为负")
    private Integer freeThreshold;

    /** 状态：缺省 1-启用；0-停用（停用模板不参与计价）。 */
    private Integer status;
}
