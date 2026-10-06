package org.icedamericanomall.wallet.adapter.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 币种启停请求（AD68 {@code PUT /api/admin/currency/{code}/status}，契约 §4.13.8g）。
 *
 * <p>停用仅影响 C 端列表（P36）与当前汇率查询（I61），不影响既有订单快照展示。
 */
@Data
public class StatusUpdateReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 目标状态：1=启用 0=停用。 */
    @NotNull(message = "状态不能为空")
    @Min(value = 0, message = "状态仅允许 0(停用)/1(启用)")
    @Max(value = 1, message = "状态仅允许 0(停用)/1(启用)")
    private Integer status;
}
