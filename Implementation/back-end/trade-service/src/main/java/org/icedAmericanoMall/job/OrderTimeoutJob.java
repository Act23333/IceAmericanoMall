package org.icedAmericanoMall.job;

import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedAmericanoMall.manager.OrderManager;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 超时未支付订单自动取消任务。
 * <p>
 * 支持两种调度方式：
 * <ul>
 *   <li>{@code @Scheduled} — 开发环境单机运行（xxl.job.enabled=false 时有效）</li>
 *   <li>{@code @XxlJob} — 生产环境通过 XXL-Job Admin 调度</li>
 * </ul>
 * 超时扫描与库存回滚编排下沉至 {@link OrderManager}。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderTimeoutJob {

    private final OrderManager orderManager;

    @Scheduled(fixedRate = 60_000)
    public void cancelTimeoutOrdersScheduled() {
        orderManager.cancelTimeoutOrders();
    }

    @XxlJob("cancelTimeoutOrders")
    public void cancelTimeoutOrdersXxlJob() {
        log.info("XXL-Job: cancelTimeoutOrders triggered");
        orderManager.cancelTimeoutOrders();
    }
}
