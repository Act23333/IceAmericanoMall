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
 * Scans for expired pay orders and auto-cancels them, triggering
 * order cancellation and stock restoration on the trade-service side.
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
