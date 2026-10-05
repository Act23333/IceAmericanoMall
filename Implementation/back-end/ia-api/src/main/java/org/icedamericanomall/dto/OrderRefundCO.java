package org.icedamericanomall.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 无售后单退款命令（trade-service → pay-service，契约 §4.14.15b I55，V5.14）。
 *
 * <p>幂等锚点 = {@code sceneRef}（refund_order.uk_biz_ref；拼团场景 = "GB:{orderNo}"，ADR-049）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderRefundCO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 关联订单号（定位原支付单；退款金额 = 订单实付全额） */
    @NotBlank
    private String orderNo;

    /** 幂等业务键（refund_order.uk_biz_ref，如 "GB:ord-xxx"） */
    @NotBlank
    private String sceneRef;

    /** 退款原因摘要（可选） */
    private String reason;
}
