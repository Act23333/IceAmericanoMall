package org.icedamericanomall.pay.infrastructure.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.icedamericanomall.pay.domain.enums.PayChannelEnum;
import org.icedamericanomall.pay.app.PayManager;
import org.icedamericanomall.common.config.RocketMqTopics;
import org.icedamericanomall.common.event.OrderCreatedEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 订单创建事件消费者 — pay-service（基础设施消息层：MQ 收发归 {@code infrastructure.messaging}）。
 *
 * <p>监听 order.created → 委托 {@link PayManager} 编排发起支付；
 * 降级口径：{@code rocketmq.enabled=false} 时不注册本消费者（走 Feign 同步链路）。
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

    /** 事件缺省渠道：上游未声明支付渠道时按微信发起。 */
    private static final String DEFAULT_PAY_CHANNEL = PayChannelEnum.WECHAT.getCode();

    private final PayManager payManager;

    @Override
    public void onMessage(OrderCreatedEvent event) {
        log.info("Received order.created: orderNo={}, userId={}, amount={}",
                event.getOrderNo(), event.getUserId(), event.getPayAmount());
        try {
            String channel = event.getPayChannel() != null ? event.getPayChannel() : DEFAULT_PAY_CHANNEL;
            payManager.initiatePayment(event.getOrderNo(), event.getUserId(),
                    PayChannelEnum.valueOf(channel));
            log.info("支付发起成功(order.created): orderNo={}", event.getOrderNo());
        } catch (Exception e) {
            // 与 RocketOrderTimeoutPublisher 策略一致：记录日志后 rethrow，交由 MQ 重试，禁止静默吞掉
            log.error("支付发起失败(order.created), 将由 MQ 重试: orderNo={}", event.getOrderNo(), e);
            throw e;
        }
    }
}
