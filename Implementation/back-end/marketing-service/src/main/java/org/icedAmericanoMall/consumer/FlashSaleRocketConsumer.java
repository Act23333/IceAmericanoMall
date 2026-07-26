package org.icedAmericanoMall.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.icedAmericanoMall.domain.dto.FlashSaleOrderMessage;
import org.icedAmericanoMall.domain.entity.FlashSaleEntity;
import org.icedAmericanoMall.service.impl.FlashSaleServiceImpl;
import org.noLazy.common.config.RocketMqTopics;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * V4.0: 秒杀异步订单 RocketMQ 消费者 — 大厂标准（京东漏斗模型第三层）。
 * <p>
 * 漏斗链路: Redis Lua 预扣 → Producer 发 RocketMQ → Consumer 异步写 MySQL + @Version 乐观锁。
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
        log.info("Flash order RocketMQ: orderNo={}, flashId={}, userId={}",
                msg.getOrderNo(), msg.getFlashId(), msg.getUserId());
        try {
            FlashSaleEntity fs = flashSaleService.getById(msg.getFlashId());
            if (fs == null || fs.getSoldCount() >= fs.getStock()) {
                log.warn("Flash {} sold out, skip order {}", msg.getFlashId(), msg.getOrderNo());
                return;
            }
            // V4.0: @Version 乐观锁 + 条件 UPDATE（数据库最终防线）
            boolean ok = flashSaleService.lambdaUpdate()
                    .eq(FlashSaleEntity::getId, msg.getFlashId())
                    .eq(FlashSaleEntity::getVersion, fs.getVersion())
                    .lt(FlashSaleEntity::getSoldCount, fs.getStock())
                    .setSql("sold_count = sold_count + 1")
                    .update();
            if (!ok) {
                log.warn("Optimistic lock failed for {}", msg.getOrderNo());
                throw new RuntimeException("Optimistic lock failed — retry");
            }
            log.info("Flash order persisted: {}", msg.getOrderNo());
        } catch (Exception e) {
            log.error("Flash order consume failed: {}", msg.getOrderNo(), e);
            throw e; // CONSUME_LATER → RocketMQ 重试（最多16次）
        }
    }
}
