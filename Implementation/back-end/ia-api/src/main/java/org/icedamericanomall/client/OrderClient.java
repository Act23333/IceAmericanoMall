package org.icedamericanomall.client;

import org.icedamericanomall.dto.CreateOrderInternalReq;
import org.icedamericanomall.dto.OrderSummaryCO;
import org.icedamericanomall.fallback.OrderClientFallback;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Feign client for trade-service internal order endpoints.
 * Used by pay-service to update order status and query order amount.
 *
 * <p>降级语义：<b>全部方法 fail-closed</b>（抛 {@code BizException}）。
 * 本 Client 承载「资金 + 订单状态」同步，静默降级会造成「已付款订单仍停在待付款」
 * 或「秒杀资格已扣但订单未落库」。详见 {@link OrderClientFallback}。
 */
@FeignClient(name = "trade-service", path = "/internal/trade/order", contextId = "order",
        fallbackFactory = OrderClientFallback.class)
public interface OrderClient {

    /**
     * Update order status after payment success / timeout.
     */
    @PutMapping("/{orderNo}/status")
    void updateOrderStatus(@PathVariable String orderNo, @RequestParam Integer status);

    /**
     * Get order summary (for payment amount retrieval).
     */
    @GetMapping("/{orderNo}")
    OrderSummaryCO getOrder(@PathVariable String orderNo);

    /**
     * V4.1: 营销服务调用 — 创建秒杀订单（统一订单中心）。
     * marketing-service 完成 Redis 预扣后，通过此接口委托 trade-service 落库。
     */
    @PostMapping("/create")
    OrderSummaryCO createOrder(@RequestBody CreateOrderInternalReq req);
}
