package org.icedamericanomall.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 币种当前汇率（trade-service 下单快照消费，契约 §4.14.3b I61，V5.17）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CurrencyRateCO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 币种代码（ISO 4217） */
    private String code;

    /** 定点整数汇率：每 1 元人民币兑换目标币主单位数 × 10⁶（如 USD 0.140300 → 140300） */
    private Long rateScaled;

    /** 目标币种 ISO 4217 最小单位指数（USD/EUR=2、JPY/KRW=0） */
    private Integer exponent;
}
