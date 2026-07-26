package org.icedAmericanoMall.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.icedAmericanoMall.domain.entity.OrderEntity;
import org.icedAmericanoMall.enums.OrderStatusEnum;
import org.icedAmericanoMall.manager.OrderLifecycleManager;
import org.icedAmericanoMall.service.OrderService;
import org.noLazy.common.config.RocketMqTopics;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "rocketmq.enabled", havingValue = "true")
@RocketMQMessageListener(
        topic = RocketMqTopics.ORDER_TIMEOUT_TOPIC,
        selectorExpression = RocketMqTopics.ORDER_TIMEOUT_TAG,
        consumerGroup = "trade-order-timeout-consumer")
public class OrderTimeoutConsumer implements RocketMQListener<String> {

    private final OrderService orderService;
    private final OrderLifecycleManager lifecycleManager;

    @Override
    public void onMessage(String orderNo) {
        log.info("Order timeout message received: orderNo={}", orderNo);
        try {
            OrderEntity order = orderService.getByOrderNo(orderNo);
            if (order == null) {
                log.warn("Order not found: orderNo={}", orderNo);
                return;
            }
            if (order.getStatus() != OrderStatusEnum.PENDING_PAYMENT.getCode()) {
                log.info("Order already processed, skip timeout: orderNo={}, status={}",
                        orderNo, order.getStatus());
                return;
            }
            lifecycleManager.cancelOrderSafely(orderNo, order.getUserId());
            log.info("Order cancelled by timeout message: orderNo={}", orderNo);
        } catch (Exception e) {
            log.error("Failed to cancel timeout order: orderNo={}", orderNo, e);
            throw e;
        }
    }
}
