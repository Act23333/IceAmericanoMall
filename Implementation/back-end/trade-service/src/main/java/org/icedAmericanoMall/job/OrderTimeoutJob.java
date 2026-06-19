package org.icedAmericanoMall.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xxl.job.core.handler.annotation.XxlJob;
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
 * 超时未支付订单自动取消任务。
 * <p>
 * 支持两种调度方式：
 * <ul>
 *   <li>{@code @Scheduled} — 开发环境单机运行（xxl.job.enabled=false 时有效）</li>
 *   <li>{@code @XxlJob} — 生产环境通过 XXL-Job Admin 调度（xxl.job.enabled=true 时由 Admin 触发）</li>
 * </ul>
 *
 * <pre>
 * Scenario: 30分钟未支付自动取消
 *   Given 订单状态为"待付款"
 *   And 订单创建时间超过30分钟
 *   When 定时任务每分钟扫描一次
 *   Then 订单状态变更为"已取消"
 *   And 回滚已扣减的 SKU 库存
 * </pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderTimeoutJob {

    private final OrderService orderService;
    private final SkuClient skuClient;
    private final OrderItemMapper orderItemMapper;

    /**
     * 开发/单机环境：@Scheduled 每 60 秒扫描一次。
     * 多实例部署时需切换到 XXL-Job 调度（xxl.job.enabled=true）。
     */
    @Scheduled(fixedRate = 60_000)
    public void cancelTimeoutOrdersScheduled() {
        doCancelTimeoutOrders();
    }

    /**
     * 生产/集群环境：XXL-Job Admin 触发，支持分片和故障转移。
     * 在 XXL-Job Admin 中配置 JobHandler = "cancelTimeoutOrders"，cron = 0/1 * * * * ?
     */
    @XxlJob("cancelTimeoutOrders")
    public void cancelTimeoutOrdersXxlJob() {
        log.info("XXL-Job: cancelTimeoutOrders triggered");
        doCancelTimeoutOrders();
    }

    private void doCancelTimeoutOrders() {
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
