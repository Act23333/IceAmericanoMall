package org.icedamericanomall.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.manager.PayManager;
import org.noLazy.common.config.RabbitMqConfig;
import org.noLazy.common.event.OrderCreatedEvent;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 支付事件监听器 — 仅在 rabbitmq.enabled=true 时激活。
 * <p>
 * 降级方案：rabbitmq.enabled=false 时，支付流程继续使用
 * trade-service → pay-service 的同步 Feign 调用。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "rabbitmq.enabled", havingValue = "true")
public class PaymentEventListener {

    private final PayManager payManager;

    @RabbitListener(queues = RabbitMqConfig.QUEUE_ORDER_CREATED)
    public void onOrderCreated(OrderCreatedEvent event) {
        log.info("Received OrderCreatedEvent: orderNo={}, userId={}, amount={}",
                event.getOrderNo(), event.getUserId(), event.getTotalAmount());
        try {
            // 事件驱动自动建单默认走微信渠道；用户可在支付时改选其他渠道
            payManager.initiatePayment(event.getOrderNo(), event.getUserId(),
                    org.icedamericanomall.enums.PayChannelEnum.WECHAT);
        } catch (Exception e) {
            log.error("Failed to auto-create payment for order: {}", event.getOrderNo(), e);
        }
    }
}
