package org.icedamericanomall.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 全额退款发票联动入参（pay-service → trade-service，契约 §4.14.16c I56，V5.15）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceVoidCO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 关联订单号（定位该订单的有效发票） */
    @NotBlank
    private String orderNo;

    /** 退款幂等键（refund_order.biz_ref，审计追溯用） */
    @NotBlank
    private String sceneRef;
}
