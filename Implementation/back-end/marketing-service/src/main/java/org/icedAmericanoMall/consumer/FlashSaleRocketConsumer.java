package org.icedAmericanoMall.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.icedAmericanoMall.domain.entity.FlashSaleEntity;
import org.icedAmericanoMall.service.impl.FlashSaleServiceImpl;
import org.noLazy.common.config.RocketMqTopics;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * V4.0: 秒杀异步订单 RocketMQ 消费者 — 大厂标准（替代 RabbitMQ FlashSaleConsumer）。
 * <p>
 * 漏斗第三层：Redis Lua 预扣 → RocketMQ 削峰 → 消费者异步写 MySQL。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "rocketmq.enabled", havingValue = "true")
@RocketMQMessageListener(
        topic = RocketMqTopics.FLASH_ORDER_TOPIC,
        selectorExpression = RocketMqTopics.FLASH_ORDER_TAG,
        consumerGroup = "marketing-flash-order-consumer")
public class FlashSaleRocketConsumer implements RocketMQListener<String> {

    private final FlashSaleServiceImpl flashSaleService;

    @Override
    public void onMessage(String flashOrderJson) {
        log.info("Flash order RocketMQ message received: {}", flashOrderJson);
        try {
            // flashOrderJson: "flashId:userId:orderNo"
            String[] parts = flashOrderJson.split(":");
            Long flashId = Long.valueOf(parts[0]);

            FlashSaleEntity fs = flashSaleService.getById(flashId);
            if (fs == null || fs.getSoldCount() >= fs.getStock()) {
                log.warn("Flash {} sold out", flashId);
                return;
            }
            // @Version 乐观锁 + 条件 UPDATE（数据库最终防线）
            boolean ok = flashSaleService.lambdaUpdate()
                    .eq(FlashSaleEntity::getId, flashId)
                    .eq(FlashSaleEntity::getVersion, fs.getVersion())
                    .lt(FlashSaleEntity::getSoldCount, fs.getStock())
                    .setSql("sold_count = sold_count + 1")
                    .update();
            if (!ok) {
                log.warn("Flash order optimistic lock failed for {}", flashOrderJson);
                throw new RuntimeException("Optimistic lock failed");
            }
            log.info("Flash order persisted via RocketMQ: {}", flashOrderJson);
        } catch (Exception e) {
            log.error("Flash order consume failed: {}", flashOrderJson, e);
            throw e; // CONSUME_LATER → RocketMQ 重试
        }
    }
}
