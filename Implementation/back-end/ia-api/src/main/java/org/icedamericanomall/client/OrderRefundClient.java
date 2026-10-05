package org.icedamericanomall.client;

import org.icedamericanomall.dto.OrderRefundCO;
import org.icedamericanomall.fallback.OrderRefundClientFallback;
import org.icedamericanomall.dto.RefundResultCO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 无售后单退款命令 Feign 客户端（F13 拼团：trade-service → pay-service，契约 §4.14.15b I55，V5.14）。
 *
 * <p>调用方：trade-service（拼团参团失败/团失败的自动退款闭环）。
 *
 * <p><b>降级语义：fail-closed（抛 {@code BizException}）</b>，与 {@code RefundClient}（I40）同口径——
 * 资金命令静默降级 = 调用方以为已退款、钱未退（ADR-035 §5 定类不变，ADR-049 幂等锚点泛化）。
 */
@FeignClient(name = "pay-service", path = "/internal/pay/refund/order",
        contextId = "orderRefund", fallbackFactory = OrderRefundClientFallback.class)
public interface OrderRefundClient {

    /** 按订单全额退款（幂等锚点 sceneRef = refund_order.uk_biz_ref）。 */
    @PostMapping
    RefundResultCO refundByOrder(@RequestBody OrderRefundCO refund);
}
