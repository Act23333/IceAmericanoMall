package org.icedAmericanoMall.producer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.noLazy.common.config.RocketMqTopics;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "rocketmq.enabled", havingValue = "true")
public class RocketOrderTimeoutPublisher implements OrderTimeoutPublisher {

    private static final long SEND_TIMEOUT_MS = 3_000L;

    private final RocketMQTemplate rocketMQTemplate;

    @Override
    public void publishTimeout(String orderNo) {
        try {
            rocketMQTemplate.syncSend(
                    RocketMqTopics.ORDER_TIMEOUT_DESTINATION,
                    MessageBuilder.withPayload(orderNo).build(),
                    SEND_TIMEOUT_MS,
                    RocketMqTopics.ORDER_TIMEOUT_DELAY_LEVEL);
            log.info("Order timeout message published: orderNo={}", orderNo);
        } catch (Exception e) {
            log.error("Failed to publish order timeout message: orderNo={}", orderNo, e);
        }
    }
}
