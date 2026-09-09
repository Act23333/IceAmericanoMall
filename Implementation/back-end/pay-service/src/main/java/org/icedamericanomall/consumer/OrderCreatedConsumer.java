package org.icedamericanomall.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.icedamericanomall.enums.PayChannelEnum;
import org.icedamericanomall.manager.PayManager;
import org.noLazy.common.config.RocketMqTopics;
import org.noLazy.common.event.OrderCreatedEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 订单创建事件消费者 — pay-service
 *
 * 监听 order.created → 自动发起支付（降级: rocketmq.enabled=false 时走 Feign 同步）
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "rocketmq.enabled", havingValue = "true")
@RocketMQMessageListener(
        topic = RocketMqTopics.DOMAIN_EVENT_TOPIC,
        selectorExpression = RocketMqTopics.TAG_ORDER_CREATED,
        consumerGroup = "pay-order-created-group")
public class OrderCreatedConsumer implements RocketMQListener<OrderCreatedEvent> {

    private final PayManager payManager;

    @Override
    public void onMessage(OrderCreatedEvent event) {
        log.info("Received order.created: orderNo={}, userId={}, amount={}",
                event.getOrderNo(), event.getUserId(), event.getPayAmount());
        try {
            String channel = event.getPayChannel() != null ? event.getPayChannel() : "WECHAT";
            payManager.initiatePayment(event.getOrderNo(), event.getUserId(),
                    PayChannelEnum.valueOf(channel));
            log.info("支付发起成功(order.created): orderNo={}", event.getOrderNo());
        } catch (Exception e) {
            log.error("支付发起失败(order.created): orderNo={}", event.getOrderNo(), e);
        }
    }
}
