package org.icedamericanomall.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.icedamericanomall.domain.dto.FlashSaleOrderMessage;
import org.icedamericanomall.domain.entity.FlashSaleEntity;
import org.icedamericanomall.service.impl.FlashSaleServiceImpl;
import org.noLazy.common.config.RocketMqTopics;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * V4.1: 秒杀异步统计 + 补偿对账 RocketMQ 消费者。
 * <p>
 * V4.1 职责简化：订单创建已移至 trade-service 同步调用（漏斗第三层），
 * 本消费者仅做异步统计（soldCount +1）和补偿对账。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "rocketmq.enabled", havingValue = "true")
@RocketMQMessageListener(
        topic = RocketMqTopics.FLASH_ORDER_TOPIC,
        selectorExpression = RocketMqTopics.FLASH_ORDER_TAG,
        consumerGroup = "marketing-flash-order-consumer")
public class FlashSaleRocketConsumer implements RocketMQListener<FlashSaleOrderMessage> {

    private final FlashSaleServiceImpl flashSaleService;

    @Override
    public void onMessage(FlashSaleOrderMessage msg) {
        log.info("Flash async stats: orderNo={}, flashId={}, userId={}",
                msg.getOrderNo(), msg.getFlashId(), msg.getUserId());
        try {
            FlashSaleEntity fs = flashSaleService.getById(msg.getFlashId());
            if (fs == null || fs.getSoldCount() >= fs.getStock()) {
                log.warn("Flash {} sold out, skip stats {}", msg.getFlashId(), msg.getOrderNo());
                return;
            }
            // V4.1: 异步统计 soldCount（订单已由 trade-service 同步创建）
            boolean ok = flashSaleService.lambdaUpdate()
                    .eq(FlashSaleEntity::getId, msg.getFlashId())
                    .eq(FlashSaleEntity::getVersion, fs.getVersion())
                    .lt(FlashSaleEntity::getSoldCount, fs.getStock())
                    .setSql("sold_count = sold_count + 1")
                    .update();
            if (!ok) {
                log.warn("Optimistic lock failed for stats {}", msg.getOrderNo());
                throw new RuntimeException("Optimistic lock failed — retry");
            }
            log.info("Flash async stats done: {}", msg.getOrderNo());
        } catch (Exception e) {
            log.error("Flash stats consume failed: {}", msg.getOrderNo(), e);
            throw e; // CONSUME_LATER → RocketMQ retry
        }
    }
}
