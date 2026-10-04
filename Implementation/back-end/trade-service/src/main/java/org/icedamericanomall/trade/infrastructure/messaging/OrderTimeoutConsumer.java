package org.icedamericanomall.trade.infrastructure.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.icedamericanomall.common.config.RocketMqTopics;
import org.icedamericanomall.trade.app.order.OrderLifecycleManager;
import org.icedamericanomall.trade.domain.enums.OrderStatusEnum;
import org.icedamericanomall.trade.domain.model.order.Order;
import org.icedamericanomall.trade.domain.repository.OrderRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 订单超时延迟消息消费者 —— 30 分钟未支付订单幂等取消（第二层保障）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "rocketmq.enabled", havingValue = "true")
@RocketMQMessageListener(
        topic = RocketMqTopics.ORDER_TIMEOUT_TOPIC,
        selectorExpression = RocketMqTopics.ORDER_TIMEOUT_TAG,
        consumerGroup = "trade-order-timeout-consumer")
public class OrderTimeoutConsumer implements RocketMQListener<String> {

    private final OrderRepository orderRepository;
    private final OrderLifecycleManager lifecycleManager;

    @Override
    public void onMessage(String orderNo) {
        log.info("Order timeout message received: orderNo={}", orderNo);
        try {
            Order order = orderRepository.findByOrderNo(orderNo);
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
