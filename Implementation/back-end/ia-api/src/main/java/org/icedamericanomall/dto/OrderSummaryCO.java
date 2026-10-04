package org.icedamericanomall.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * Order summary DTO for inter-service Feign calls (trade-service → pay-service / logistics-service).
 */
@Data
public class OrderSummaryCO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String orderNo;

    /** 订单归属用户 ID —— 供下游（logistics-service）做数据归属校验。 */
    private Long userId;

    /** 订单原价（分）—— 未扣除优惠券前的商品合计，字段口径为 totalAmount = payAmount + discountAmount。 */
    private Integer totalAmount;

    /**
     * 应付金额（分）—— 买家实际需支付的金额（原价扣除优惠券等优惠后）。
     * <p>扣款/结算口径必须使用本字段，禁止用 {@link #totalAmount}（原价）发起支付。
     */
    private Integer payAmount;

    private Integer status;
}
