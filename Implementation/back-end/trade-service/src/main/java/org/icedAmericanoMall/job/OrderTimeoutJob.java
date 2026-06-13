package org.icedAmericanoMall.job;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedAmericanoMall.domain.entity.OrderEntity;
import org.icedAmericanoMall.enums.OrderStatusEnum;
import org.icedAmericanoMall.service.OrderService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Scans for unpaid orders older than 30 minutes and auto-cancels them.
 * In production, also restores stock via Feign to item-service.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderTimeoutJob {

    private final OrderService orderService;

    @Scheduled(fixedRate = 60_000) // every 60 seconds
    public void cancelTimeoutOrders() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(30);
        List<OrderEntity> timeoutOrders = orderService.lambdaQuery()
                .eq(OrderEntity::getStatus, OrderStatusEnum.PENDING_PAYMENT.getCode())
                .lt(OrderEntity::getCreateTime, cutoff)
                .list();

        for (OrderEntity order : timeoutOrders) {
            try {
                orderService.lambdaUpdate()
                        .eq(OrderEntity::getId, order.getId())
                        .set(OrderEntity::getStatus, OrderStatusEnum.CANCELLED.getCode())
                        .set(OrderEntity::getCloseTime, LocalDateTime.now())
                        .update();
                log.info("Auto-cancelled timeout order: {}", order.getOrderNo());
            } catch (Exception e) {
                log.error("Failed to auto-cancel order: {}", order.getOrderNo(), e);
            }
        }
    }
}
