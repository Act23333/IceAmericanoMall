package org.icedAmericanoMall.controller;

import lombok.RequiredArgsConstructor;
import org.icedAmericanoMall.domain.entity.OrderEntity;
import org.icedAmericanoMall.enums.OrderStatusEnum;
import org.icedAmericanoMall.service.OrderService;
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
     * Get order summary — called by pay-service to retrieve totalAmount for payment initiation.
     */
    @GetMapping("/{orderNo}")
    public OrderEntity getOrder(@PathVariable String orderNo) {
        return orderService.getByOrderNo(orderNo);
    }
}
