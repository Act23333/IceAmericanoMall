package org.noLazy.common.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.noLazy.common.config.RabbitMqConfig;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 领域事件发布器 — 仅在 rabbitmq.enabled=true 时激活。
 * <p>
 * 降级方案：rabbitmq.enabled=false（默认）时，此 Bean 不会创建，
 * 调用方应保留现有同步 Feign 逻辑。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "rabbitmq.enabled", havingValue = "true")
public class EventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public void publish(DomainEvent event, String routingKey) {
        try {
            event.setSource(routingKey);
            rabbitTemplate.convertAndSend(RabbitMqConfig.EXCHANGE, routingKey, event);
            log.info("Event published: {} -> {}", routingKey, event.getEventId());
        } catch (Exception e) {
            log.error("Failed to publish event: {}", routingKey, e);
        }
    }
}
