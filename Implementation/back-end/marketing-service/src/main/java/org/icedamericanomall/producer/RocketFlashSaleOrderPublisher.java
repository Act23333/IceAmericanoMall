package org.icedamericanomall.producer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.icedamericanomall.domain.dto.FlashSaleOrderMessage;
import org.noLazy.common.config.RocketMqTopics;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "rocketmq.enabled", havingValue = "true")
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
