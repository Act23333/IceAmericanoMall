package org.icedamericanomall.wallet.adapter.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 汇率人工修正请求（AD67 {@code PUT /api/admin/currency/{code}/rate}，契约 §4.13.8g）。
 *
 * <p><b>定点整数直传</b>：{@code rateScaled} 为 10⁻⁶ 刻度定点整数（USD 0.140300 → 140300），
 * 主单位小数由管理端 UI 换算，后端不做浮点解析。多词字段走**全局 snake_case**（契约口径
 * {@code rate_scaled}）。
 */
@Data
public class RateUpdateReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 定点整数汇率（10⁻⁶ 刻度，必填且 > 0）。 */
    @NotNull(message = "定点整数汇率不能为空")
    @Positive(message = "定点整数汇率必须大于0")
    private Long rateScaled;

    /** 备注（可空，≤255 字符）。 */
    @Size(max = 255, message = "备注不能超过255字符")
    private String remark;
}
