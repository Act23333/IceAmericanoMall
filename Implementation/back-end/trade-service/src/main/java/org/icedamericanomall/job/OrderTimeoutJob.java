package org.icedamericanomall.job;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * V4.0: 订单超时取消已改为 RocketMQ 延迟消息（大厂标准）。
 * <p>
 * 旧方案: @Scheduled(fixedRate=60s) 轮询全表扫描 → 无论有无数据每 60s 消耗 CPU。
 * V3.6: RabbitMQ TTL + DLX 死信队列 → 精度有限(分钟级)。
 * V4.0: RocketMQ 延迟消息(level=16, 30min) → 大厂标准，秒级精度。
 * <p>
 * 本类保留为占位，生产环境无需任何定时任务。
 *
 * @see org.icedamericanomall.consumer.OrderTimeoutConsumer
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
