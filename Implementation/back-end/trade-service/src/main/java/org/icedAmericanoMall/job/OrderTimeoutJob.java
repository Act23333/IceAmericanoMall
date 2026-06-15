package org.icedAmericanoMall.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedAmericanoMall.client.SkuClient;
import org.icedAmericanoMall.domain.entity.OrderEntity;
import org.icedAmericanoMall.domain.entity.OrderItemEntity;
import org.icedAmericanoMall.dto.StockOpDTO;
import org.icedAmericanoMall.enums.OrderStatusEnum;
import org.icedAmericanoMall.mapper.OrderItemMapper;
import org.icedAmericanoMall.service.OrderService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 超时未支付订单自动取消定时任务。
 *
 * <pre>
 * Scenario: 30分钟未支付自动取消
 *   Given 订单状态为"待付款"
 *   And 订单创建时间超过30分钟
 *   When 定时任务每分钟扫描一次
 *   Then 订单状态变更为"已取消"
 *   And 回滚已扣减的 SKU 库存
 *
 * Scenario: 单个订单取消失败不影响其他
 *   Given 存在多个超时订单
 *   When 其中某个订单取消或库存恢复失败
 *   Then 仅记录错误日志
 *   And 继续处理下一个订单
 * </pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderTimeoutJob {

    private final OrderService orderService;
    private final SkuClient skuClient;
    private final OrderItemMapper orderItemMapper;

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

                // Restore stock for this order
                restoreOrderStock(order.getId());

                log.info("Auto-cancelled timeout order: {}", order.getOrderNo());
            } catch (Exception e) {
                log.error("Failed to auto-cancel order: {}", order.getOrderNo(), e);
            }
        }
    }

    private void restoreOrderStock(Long orderId) {
        List<OrderItemEntity> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItemEntity>()
                        .eq(OrderItemEntity::getOrderId, orderId));
        if (items.isEmpty()) {
            return;
        }
        List<StockOpDTO> stockOps = items.stream().map(item -> {
            StockOpDTO op = new StockOpDTO();
            op.setSkuId(item.getSkuId());
            op.setQuantity(item.getQuantity());
            return op;
        }).collect(Collectors.toList());

        try {
            skuClient.restoreStock(stockOps);
        } catch (Exception e) {
            log.error("超时取消订单库存恢复失败，需人工处理: orderId={}", orderId, e);
        }
    }
}
