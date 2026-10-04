package org.icedamericanomall.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 退款发起请求（跨服务契约对象）—— trade-service → pay-service（I40 {@code POST /internal/pay/refund}）。
 *
 * <p><b>幂等</b>：{@code afterSaleId} 是幂等键——pay-service 侧 {@code refund_order.uk_after_sale}
 * 唯一约束保证「一售后单一次退款」，同一 {@code afterSaleId} 重复请求返回既有退款单。
 *
 * <p><b>金额</b>：{@code refundAmount} 单位为<b>分</b>（{@code > 0}）；申请售后时已校验
 * {@code ≤ 订单实付 payAmount}，pay-service 侧再以支付单金额做双保险校验。
 *
 * <p><b>字段命名</b>：本契约的两个端点（pay-service / trade-service）均为默认 camelCase Jackson
 * 口径，故无显式 {@code @JsonProperty} 覆盖。
 */
@Data
public class RefundCreateCO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 售后单 ID（退款幂等键）。 */
    private Long afterSaleId;

    /** 订单号（{@code orders.order_no}）。 */
    private String orderNo;

    /** 退款金额（分，{@code > 0}）。 */
    private Integer refundAmount;

    /** 退款原因摘要（可空，落 {@code refund_order} 日志/原因）。 */
    private String reason;
}
