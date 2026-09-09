package org.icedamericanomall.job;

import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.manager.PayManager;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 支付超时自动取消任务。
 * <p>
 * 支持两种调度方式：
 * <ul>
 *   <li>{@code @Scheduled} — 开发环境单机运行</li>
 *   <li>{@code @XxlJob} — 生产环境通过 XXL-Job Admin 调度</li>
 * </ul>
 * 具体的超时扫描与跨服务订单取消编排下沉至 {@link PayManager}。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PayTimeoutJob {

    private final PayManager payManager;

    // V4.0: @Scheduled已废弃, 迁移到RocketMQ延迟消息
    // @Scheduled(fixedRate = 30_000)
    public void cancelTimeoutPayOrdersScheduled() {
        payManager.cancelTimeoutPayOrders();
    }

    @XxlJob("cancelTimeoutPayOrders")
    public void cancelTimeoutPayOrdersXxlJob() {
        log.info("XXL-Job: cancelTimeoutPayOrders triggered");
        payManager.cancelTimeoutPayOrders();
    }
}
