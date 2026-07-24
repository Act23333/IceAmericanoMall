package org.icedAmericanoMall.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedAmericanoMall.domain.entity.OrderEntity;
import org.icedAmericanoMall.manager.OrderLifecycleManager;
import org.icedAmericanoMall.service.OrderService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * V3.6: 订单超时取消 RabbitMQ TTL 死信消费者（大厂标准：京东 RocketMQ 延迟消息对标）。
 * <p>
 * 旧方案: @Scheduled 60s 轮询全表扫描 → CPU 浪费 + 串行 Feign。
 * 新方案: 创建订单时发 TTL=30min 消息 → 自动过期 → 死信队列 → 消费者取消。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderTimeoutConsumer {

    private final OrderService orderService;
    private final OrderLifecycleManager lifecycleManager;

    @RabbitListener(queues = "order.timeout.dlq")
    public void handleTimeoutOrder(String orderNo) {
        log.info("TTL timeout triggered for: {}", orderNo);
        try {
            OrderEntity order = orderService.getByOrderNo(orderNo);
            if (order == null) {
                log.warn("Order not found: {}", orderNo);
                return;
            }
            // V3.6 幂等保护：只有待付款状态才取消
            if (order.getStatus() != 1) {
                log.info("Order {} already processed (status={}), skip", orderNo, order.getStatus());
                return;
            }
            lifecycleManager.cancelOrderSafely(orderNo, order.getUserId());
            log.info("Order {} cancelled via TTL", orderNo);
        } catch (Exception e) {
            log.error("Failed to cancel order {}: {}", orderNo, e.getMessage());
            throw e; // nack → requeue
        }
    }
}
