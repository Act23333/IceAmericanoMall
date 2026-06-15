package org.icedAmericanoMall.job;

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
 * 支付超时自动取消定时任务。
 *
 * <pre>
 * Scenario: 支付超过有效期自动取消
 *   Given PayOrder 状态为"待支付"
 *   And payOverTime 已过期
 *   When 定时任务每30秒扫描一次
 *   Then PayOrder 状态变更为"超时取消"
 *   And 通过 Feign 将关联的 trade-service Order 状态更新为"已取消"
 *   And trade-service 侧自动触发库存回滚
 *
 * Scenario: 超时取消失败不影响其他支付单
 *   Given 存在多个超时支付单
 *   When 其中某个取消失败
 *   Then 仅记录错误日志
 *   And 继续处理下一个支付单
 * </pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PayTimeoutJob {

    private static final int ORDER_STATUS_CANCELLED = 5;

    private final PayOrderService payOrderService;
    private final OrderClient orderClient;

    @Scheduled(fixedRate = 30_000) // every 30 seconds
    public void cancelTimeoutPayOrders() {
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

                // Cancel the associated trade order (stock restore happens on trade-service side)
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
