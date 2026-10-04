package org.icedamericanomall.marketing.infrastructure.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.icedamericanomall.common.config.RocketMqTopics;
import org.icedamericanomall.marketing.domain.dto.FlashSaleOrderMessage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

/**
 * 秒杀订单消息 RocketMQ 实现。
 *
 * <p>装配开关与 {@link NoopFlashSaleOrderPublisher} 互斥：仅当
 * {@code rocketmq.producer.enabled=true} 时生效（此时 RocketMQTemplate 由 starter 提供）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "rocketmq.producer.enabled", havingValue = "true")
public class RocketFlashSaleOrderPublisher implements FlashSaleOrderPublisher {

    private static final long SEND_TIMEOUT_MS = 3_000L;

    private final RocketMQTemplate rocketMQTemplate;

    @Override
    public boolean publish(FlashSaleOrderMessage message) {
        try {
            rocketMQTemplate.syncSend(
                    RocketMqTopics.FLASH_ORDER_DESTINATION,
                    MessageBuilder.withPayload(message).build(),
                    SEND_TIMEOUT_MS);
            log.info("Flash order message published: orderNo={}", message.getOrderNo());
            return true;
        } catch (Exception e) {
            log.error("Failed to publish flash order message: orderNo={}", message.getOrderNo(), e);
            return false;
        }
    }
}
