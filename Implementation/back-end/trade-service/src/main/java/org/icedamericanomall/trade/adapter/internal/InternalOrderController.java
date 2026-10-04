package org.icedamericanomall.trade.adapter.internal;

import lombok.RequiredArgsConstructor;
import org.icedamericanomall.dto.CreateOrderInternalReq;
import org.icedamericanomall.dto.OrderSummaryCO;
import org.icedamericanomall.trade.adapter.response.OrderVO;
import org.icedamericanomall.trade.app.order.OrderCreationManager;
import org.icedamericanomall.trade.app.order.OrderLifecycleManager;
import org.icedamericanomall.trade.app.order.OrderQueryManager;
import org.icedamericanomall.trade.app.order.strategy.OrderCreateContext;
import org.icedamericanomall.trade.domain.enums.OrderTypeEnum;
import org.springframework.web.bind.annotation.*;

/**
 * Internal order endpoints for inter-service Feign calls.
 * All endpoints return raw types without Result wrapping (internal API convention).
 */
@RestController
@RequestMapping("/internal/trade/order")
@RequiredArgsConstructor
public class InternalOrderController {

    private final OrderLifecycleManager orderLifecycleManager;
    private final OrderQueryManager orderQueryManager;
    private final OrderCreationManager orderCreationManager;

    /**
     * Update order status — called by pay-service after payment success / timeout.
     * <p>状态回写走条件更新，仅接受「待付款 → 待发货」与「待付款 → 已取消」两条合法边，
     * 其余（含重复回写）抛 ORDER_STATUS_INVALID，由调用方感知失败。
     */
    @PutMapping("/{orderNo}/status")
    public void updateOrderStatus(@PathVariable String orderNo, @RequestParam Integer status) {
        orderLifecycleManager.transitionFromPendingPayment(orderNo, status);
    }

    /**
     * Get order summary by order number — returns 原价 + 应付金额 + 状态。
     */
    @GetMapping("/{orderNo}")
    public OrderSummaryCO getOrder(@PathVariable String orderNo) {
        return orderQueryManager.getOrderSummary(orderNo);
    }

    /**
     * Get order summary by technical id — 供下游（logistics-service）做订单归属校验。
     */
    @GetMapping("/id/{orderId}")
    public OrderSummaryCO getOrderById(@PathVariable Long orderId) {
        return orderQueryManager.getOrderSummaryById(orderId);
    }

    /**
     * V4.1: 创建秒杀订单 — 营销服务完成 Redis 预扣后，调用此接口创建真实订单。
     * 走统一订单中心（策略模式路由，orderType=FLASH_SALE）。
     */
    @PostMapping("/create")
    public OrderSummaryCO createOrder(@RequestBody CreateOrderInternalReq req) {
        OrderCreateContext ctx = new OrderCreateContext();
        ctx.setUserId(req.getUserId());
        ctx.setOrderType(OrderTypeEnum.FLASH_SALE);
        ctx.setSkuId(req.getSkuId());
        ctx.setQuantity(req.getQuantity());
        ctx.setAddressId(req.getAddressId());
        ctx.setFlashId(req.getFlashId());
        ctx.setFlashPrice(req.getFlashPrice());

        OrderVO vo = orderCreationManager.createOrder(ctx);

        OrderSummaryCO dto = new OrderSummaryCO();
        dto.setOrderNo(vo.getOrderNo());
        dto.setUserId(vo.getUserId());
        dto.setTotalAmount(vo.getTotalAmount());
        dto.setPayAmount(vo.getPayAmount());
        dto.setStatus(vo.getStatus());
        return dto;
    }
}
