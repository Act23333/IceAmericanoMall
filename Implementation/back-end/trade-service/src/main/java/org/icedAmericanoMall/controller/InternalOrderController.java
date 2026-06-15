package org.icedAmericanoMall.controller;

import lombok.RequiredArgsConstructor;
import org.icedAmericanoMall.domain.entity.OrderEntity;
import org.icedAmericanoMall.dto.OrderSummaryDTO;
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
}
