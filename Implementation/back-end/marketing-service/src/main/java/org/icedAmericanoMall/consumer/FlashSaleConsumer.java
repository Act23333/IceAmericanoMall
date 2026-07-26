package org.icedAmericanoMall.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.icedAmericanoMall.domain.dto.FlashSaleOrderMessage;
import org.icedAmericanoMall.service.FlashSaleService;
import org.noLazy.common.config.RocketMqTopics;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "rocketmq.enabled", havingValue = "true")
@RocketMQMessageListener(
        topic = RocketMqTopics.FLASH_ORDER_TOPIC,
        selectorExpression = RocketMqTopics.FLASH_ORDER_TAG,
        consumerGroup = "marketing-flash-order-consumer")
public class FlashSaleConsumer implements RocketMQListener<FlashSaleOrderMessage> {

    private final FlashSaleService flashSaleService;

    @Override
    public void onMessage(FlashSaleOrderMessage message) {
        log.info("Flash order message received: orderNo={}", message.getOrderNo());
        flashSaleService.confirmFlashOrder(message);
    }
}
