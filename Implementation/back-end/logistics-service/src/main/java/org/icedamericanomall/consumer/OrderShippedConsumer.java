package org.icedamericanomall.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.noLazy.common.config.RocketMqTopics;
import org.noLazy.common.event.OrderShippedEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 订单发货事件消费者 — logistics-service
 *
 * 监听 order.shipped → 创建物流记录（降级: rocketmq.enabled=false 时走 Feign 同步）
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "rocketmq.enabled", havingValue = "true")
@RocketMQMessageListener(
        topic = RocketMqTopics.DOMAIN_EVENT_TOPIC,
        selectorExpression = RocketMqTopics.TAG_ORDER_SHIPPED,
        consumerGroup = "logistics-order-shipped-group")
public class OrderShippedConsumer implements RocketMQListener<OrderShippedEvent> {

    @Override
    public void onMessage(OrderShippedEvent event) {
        log.info("Received order.shipped: orderNo={}, logisticsNo={}, company={}",
                event.getOrderNo(), event.getLogisticsNumber(), event.getLogisticsCompany());
        // 物流记录已在 OrderLifecycleManager.shipOrder 中通过 Feign 同步创建，
        // 此消费者用于异步通知（如 WebSocket 推送、短信通知等），V5.1 实现。
    }
}
