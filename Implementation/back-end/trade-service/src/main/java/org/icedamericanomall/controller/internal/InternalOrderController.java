package org.icedamericanomall.controller.internal;

import lombok.RequiredArgsConstructor;
import org.icedamericanomall.domain.entity.OrderEntity;
import org.icedamericanomall.domain.vo.OrderVO;
import org.icedamericanomall.dto.CreateOrderInternalReq;
import org.icedamericanomall.dto.OrderSummaryDTO;
import org.icedamericanomall.enums.OrderTypeEnum;
import org.icedamericanomall.manager.OrderCreationManager;
import org.icedamericanomall.service.OrderService;
import org.icedamericanomall.strategy.OrderCreateContext;
import org.springframework.web.bind.annotation.*;

/**
 * Internal order endpoints for inter-service Feign calls.
 * All endpoints return raw types without Result wrapping (internal API convention).
 */
@RestController
@RequestMapping("/internal/trade/order")
@RequiredArgsConstructor
public class InternalOrderController {

    private final OrderService orderService;
    private final OrderCreationManager orderCreationManager;

    /**
     * Update order status — called by pay-service after payment success / timeout.
     */
    @PutMapping("/{orderNo}/status")
    public void updateOrderStatus(@PathVariable String orderNo, @RequestParam Integer status) {
        OrderEntity order = orderService.getByOrderNo(orderNo);
        if (order == null) {
            return;
        }
        orderService.lambdaUpdate()
                .eq(OrderEntity::getId, order.getId())
                .set(OrderEntity::getStatus, status)
                .update();
    }

    /**
     * Get order summary for payment initiation — returns totalAmount + status.
     */
    @GetMapping("/{orderNo}")
    public OrderSummaryDTO getOrder(@PathVariable String orderNo) {
        OrderEntity order = orderService.getByOrderNo(orderNo);
        if (order == null) {
            return null;
        }
        OrderSummaryDTO dto = new OrderSummaryDTO();
        dto.setOrderNo(order.getOrderNo());
        dto.setTotalAmount(order.getTotalAmount());
        dto.setStatus(order.getStatus());
        return dto;
    }

    /**
     * V4.1: 创建秒杀订单 — 营销服务完成 Redis 预扣后，调用此接口创建真实订单。
     * 走统一订单中心（策略模式路由，orderType=FLASH_SALE）。
     */
    @PostMapping("/create")
    public OrderSummaryDTO createOrder(@RequestBody CreateOrderInternalReq req) {
        OrderCreateContext ctx = new OrderCreateContext();
        ctx.setUserId(req.getUserId());
        ctx.setOrderType(OrderTypeEnum.FLASH_SALE);
        ctx.setSkuId(req.getSkuId());
        ctx.setQuantity(req.getQuantity());
        ctx.setAddressId(req.getAddressId());
        ctx.setFlashId(req.getFlashId());
        ctx.setFlashPrice(req.getFlashPrice());

        OrderVO vo = orderCreationManager.createOrder(ctx);

        OrderSummaryDTO dto = new OrderSummaryDTO();
        dto.setOrderNo(vo.getOrderNo());
        dto.setTotalAmount(vo.getPayAmount());
        dto.setStatus(vo.getStatus());
        return dto;
    }
}
