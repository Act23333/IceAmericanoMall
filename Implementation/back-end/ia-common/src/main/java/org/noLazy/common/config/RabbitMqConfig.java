package org.noLazy.common.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 配置 — 仅在 rabbitmq.enabled=true 时激活。
 * 声明领域事件所需的 Queue / Exchange / Binding。
 */
@Configuration
public class RabbitMqConfig {

    public static final String EXCHANGE = "icedmall.events";
    public static final String QUEUE_ORDER_CREATED = "order.created";
    public static final String QUEUE_PAYMENT_SUCCEEDED = "payment.succeeded";
    public static final String QUEUE_ORDER_SHIPPED = "order.shipped";

    @Bean
    @ConditionalOnProperty(name = "rabbitmq.enabled", havingValue = "true")
    public TopicExchange eventExchange() {
        return new TopicExchange(EXCHANGE);
    }

    @Bean
    @ConditionalOnProperty(name = "rabbitmq.enabled", havingValue = "true")
    public Queue orderCreatedQueue() {
        return QueueBuilder.durable(QUEUE_ORDER_CREATED).build();
    }

    @Bean
    @ConditionalOnProperty(name = "rabbitmq.enabled", havingValue = "true")
    public Queue paymentSucceededQueue() {
        return QueueBuilder.durable(QUEUE_PAYMENT_SUCCEEDED).build();
    }

    @Bean
    @ConditionalOnProperty(name = "rabbitmq.enabled", havingValue = "true")
    public Queue orderShippedQueue() {
        return QueueBuilder.durable(QUEUE_ORDER_SHIPPED).build();
    }

    @Bean
    @ConditionalOnProperty(name = "rabbitmq.enabled", havingValue = "true")
    public Binding orderCreatedBinding() {
        return BindingBuilder.bind(orderCreatedQueue()).to(eventExchange()).with(QUEUE_ORDER_CREATED);
    }

    @Bean
    @ConditionalOnProperty(name = "rabbitmq.enabled", havingValue = "true")
    public Binding paymentSucceededBinding() {
        return BindingBuilder.bind(paymentSucceededQueue()).to(eventExchange()).with(QUEUE_PAYMENT_SUCCEEDED);
    }

    @Bean
    @ConditionalOnProperty(name = "rabbitmq.enabled", havingValue = "true")
    public Binding orderShippedBinding() {
        return BindingBuilder.bind(orderShippedQueue()).to(eventExchange()).with(QUEUE_ORDER_SHIPPED);
    }

    @Bean
    @ConditionalOnProperty(name = "rabbitmq.enabled", havingValue = "true")
    public Jackson2JsonMessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
