package org.icedamericanomall.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 部分退款发票标记入参（pay-service → trade-service，契约 §4.14.16c I60，V5.16.1）。
 *
 * <p>触发条件（pay 侧判定）：退款成功且 {@code refundAmount < 支付单金额}（部分退款）。
 * 语义：该订单有效发票（已开具）置 {@code partial_refund_flag=1} 待人工处理（PRD D4/O6）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InvoicePartialRefundCO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 关联订单号（定位该订单的有效发票） */
    @NotBlank
    private String orderNo;

    /** 退款幂等键（refund_order.biz_ref，审计追溯用） */
    @NotBlank
    private String sceneRef;
}
