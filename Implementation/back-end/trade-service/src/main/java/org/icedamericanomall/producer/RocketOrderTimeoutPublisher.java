package org.icedamericanomall.producer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.noLazy.common.config.RocketMqTopics;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

/**
 * V4.0: RocketMQ 订单超时延迟消息发布者（大厂标准: 阿里/京东 RocketMQ 延迟消息）。
 * <p>
 * 下单成功后调用，发送 delay level=16 (30min) 延迟消息。
 * 30min 后消息投递 → {@link org.icedamericanomall.consumer.OrderTimeoutConsumer} 消费 → 幂等取消。
 */
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
            log.error("Order timeout publish FAILED — manual intervention needed: orderNo={}", orderNo, e);
            // Alibaba 规范: 不吞异常。RocketMQ 不可达时不应静默丢失，需触发人工处理
            throw new org.noLazy.common.exception.BizException(
                    org.noLazy.common.enums.ErrorCode.BUSINESS_EXECUTION_EXCEPTION,
                    "订单超时消息发送失败: " + orderNo);
        }
    }
}
