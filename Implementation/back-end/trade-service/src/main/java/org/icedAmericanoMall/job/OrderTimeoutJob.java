package org.icedAmericanoMall.job;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * V3.6: 订单超时取消已改为事件驱动（RabbitMQ TTL 死信队列）。
 * <p>
 * 旧方案: @Scheduled(fixedRate=60s) 轮询全表扫描 → 无论有无数据每 60s 消耗 CPU。
 * 新方案: 创建订单时发送 TTL=30min 消息到 RabbitMQ → 自动过期 → 死信消费者取消。
 * <p>
 * 本类保留为占位，生产环境无需任何定时任务。
 *
 * @see org.icedAmericanoMall.consumer.OrderTimeoutConsumer
 */
@Slf4j
@Component
public class OrderTimeoutJob {
    // V3.6: 所有调度已迁至 RabbitMQ TTL，本类为空占位。
    // 如需回退到轮询模式，取消下方注释并恢复 OrderLifecycleManager.cancelTimeoutOrders()
    //
    // @Scheduled(fixedRate = 60_000)
    // public void cancelTimeoutOrdersScheduled() { ... }
    //
    // @XxlJob("cancelTimeoutOrders")
    // public void cancelTimeoutOrdersXxlJob() { ... }
}
