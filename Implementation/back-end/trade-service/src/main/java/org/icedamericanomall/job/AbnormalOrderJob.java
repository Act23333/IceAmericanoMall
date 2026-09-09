package org.icedamericanomall.job;

import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.domain.entity.OrderEntity;
import org.icedamericanomall.manager.OrderLifecycleManager;
import org.icedamericanomall.service.OrderService;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * V3.7: 异常订单兜底扫描 — 订单超时取消第三层保障（XXL-Job 每天凌晨2点）。
 * <p>
 * 扫描近3天 status=PENDING_PAYMENT 且 createTime<now-30min 的异常订单，
 * 批量取消 + 回库存 + 回优惠券。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AbnormalOrderJob {

    private final OrderService orderService;
    private final OrderLifecycleManager lifecycleManager;

    @XxlJob("scanAbnormalOrders")
    public void scanAbnormalOrders() {
        log.info("XXL-Job: scanAbnormalOrders triggered");
        LocalDateTime threeDaysAgo = LocalDateTime.now().minusDays(3);
        LocalDateTime timeoutCutoff = LocalDateTime.now().minusMinutes(30);

        List<OrderEntity> abnormal = orderService.listTimeoutPending(timeoutCutoff);
        int canceled = 0;
        for (OrderEntity order : abnormal) {
            if (order.getCreateTime().isBefore(threeDaysAgo)) continue; // 只扫近3天
            try {
                lifecycleManager.cancelOrderSafely(order.getOrderNo(), order.getUserId());
                canceled++;
            } catch (Exception e) {
                log.error("Abnormal order cancel failed: {}", order.getOrderNo(), e);
            }
        }
        log.info("scanAbnormalOrders done: total={}, canceled={}", abnormal.size(), canceled);
        if (canceled > 10) {
            log.warn("ALERT: {} abnormal orders cancelled, may need manual review", canceled);
        }
    }
}
