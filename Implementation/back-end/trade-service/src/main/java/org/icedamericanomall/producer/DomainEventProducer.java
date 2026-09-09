package org.icedamericanomall.producer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.noLazy.common.config.RocketMqTopics;
import org.noLazy.common.event.DomainEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 领域事件 Producer — trade-service 发布订单领域事件到 RocketMQ。
 *
 * 阿里标准: 统一 domain-event-topic，按 Tag 区分事件类型。
 *
 * <pre>
 * 降级: rocketmq.enabled=false 时此 Bean 不创建，
 *       调用方保持 Feign 同步调用作为 fallback。
 * </pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "rocketmq.enabled", havingValue = "true")
public class DomainEventProducer {

    private final RocketMQTemplate rocketMQTemplate;

    /** 发布订单创建事件 */
    public void publishOrderCreated(DomainEvent event) {
        event.setSource("trade-service");
        rocketMQTemplate.syncSend(RocketMqTopics.ORDER_CREATED, event);
        log.info("Event published: order.created, eventId={}", event.getEventId());
    }

    /** 发布订单发货事件 */
    public void publishOrderShipped(DomainEvent event) {
        event.setSource("trade-service");
        rocketMQTemplate.syncSend(RocketMqTopics.ORDER_SHIPPED, event);
        log.info("Event published: order.shipped, eventId={}", event.getEventId());
    }
}
