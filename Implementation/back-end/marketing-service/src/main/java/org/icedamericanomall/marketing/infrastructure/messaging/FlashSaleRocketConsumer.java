package org.icedamericanomall.marketing.infrastructure.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.icedamericanomall.common.config.RocketMqTopics;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.marketing.app.flash.FlashSaleManager;
import org.icedamericanomall.marketing.domain.dto.FlashSaleOrderMessage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * V4.1: 秒杀异步统计 + 补偿对账 RocketMQ 消费者。
 *
 * <p>V4.1 职责简化：订单创建已移至 trade-service 同步调用（漏斗第三层），
 * 本消费者仅做异步统计（soldCount +1，委托 {@link FlashSaleManager}）和补偿对账；
 * 乐观锁冲突抛 {@link BizException} 交由 RocketMQ 重试消费。
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

    private final FlashSaleManager flashSaleManager;

    @Override
    public void onMessage(FlashSaleOrderMessage msg) {
        log.info("Flash async stats: orderNo={}, flashId={}, userId={}",
                msg.getOrderNo(), msg.getFlashId(), msg.getUserId());
        try {
            // V4.1: 异步统计 soldCount（订单已由 trade-service 同步创建）
            flashSaleManager.incrementSoldCountAsync(msg.getFlashId(), msg.getOrderNo());
        } catch (BizException e) {
            log.error("Flash stats consume failed: {}", msg.getOrderNo(), e);
            throw e; // CONSUME_LATER → RocketMQ 重试
        } catch (Exception e) {
            log.error("Flash stats consume failed: {}", msg.getOrderNo(), e);
            throw new BizException(ErrorCode.FLASH_SALE_FAILED, "秒杀统计失败，等待重试");
        }
    }
}
