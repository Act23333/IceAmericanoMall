package org.icedAmericanoMall.job;

import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedAmericanoMall.client.OrderClient;
import org.icedAmericanoMall.domain.entity.PayOrderEntity;
import org.icedAmericanoMall.enums.PayStatusEnum;
import org.icedAmericanoMall.service.PayOrderService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 支付超时自动取消任务。
 * <p>
 * 支持两种调度方式：
 * <ul>
 *   <li>{@code @Scheduled} — 开发环境单机运行</li>
 *   <li>{@code @XxlJob} — 生产环境通过 XXL-Job Admin 调度</li>
 * </ul>
 *
 * <pre>
 * Scenario: 支付超过有效期自动取消
 *   Given PayOrder 状态为"待支付"
 *   And payOverTime 已过期
 *   When 定时任务每30秒扫描一次
 *   Then PayOrder 状态变更为"超时取消"
 *   And 通过 Feign 将关联的 trade-service Order 状态更新为"已取消"
 * </pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PayTimeoutJob {

    private static final int ORDER_STATUS_CANCELLED = 5;

    private final PayOrderService payOrderService;
    private final OrderClient orderClient;

    @Scheduled(fixedRate = 30_000)
    public void cancelTimeoutPayOrdersScheduled() {
        doCancelTimeoutPayOrders();
    }

    @XxlJob("cancelTimeoutPayOrders")
    public void cancelTimeoutPayOrdersXxlJob() {
        log.info("XXL-Job: cancelTimeoutPayOrders triggered");
        doCancelTimeoutPayOrders();
    }

    private void doCancelTimeoutPayOrders() {
        LocalDateTime cutoff = LocalDateTime.now();
        List<PayOrderEntity> timeoutOrders = payOrderService.lambdaQuery()
                .eq(PayOrderEntity::getStatus, PayStatusEnum.PENDING_PAY.getCode())
                .lt(PayOrderEntity::getPayOverTime, cutoff)
                .list();

        for (PayOrderEntity payOrder : timeoutOrders) {
            try {
                payOrderService.lambdaUpdate()
                        .eq(PayOrderEntity::getId, payOrder.getId())
                        .set(PayOrderEntity::getStatus, PayStatusEnum.TIMEOUT_CANCEL.getCode())
                        .update();

                try {
                    orderClient.updateOrderStatus(payOrder.getBizOrderNo(), ORDER_STATUS_CANCELLED);
                } catch (Exception e) {
                    log.error("支付超时取消订单失败: orderNo={}", payOrder.getBizOrderNo(), e);
                }

                log.info("支付超时已取消: payOrderNo={}, orderNo={}",
                        payOrder.getPayOrderNo(), payOrder.getBizOrderNo());
            } catch (Exception e) {
                log.error("处理支付超时订单失败: payOrderId={}", payOrder.getId(), e);
            }
        }
    }
}
