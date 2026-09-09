package org.icedamericanomall.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.domain.entity.OrderLogisticsEntity;
import org.icedamericanomall.service.LogisticsService;
import org.noLazy.common.config.RabbitMqConfig;
import org.noLazy.common.event.OrderShippedEvent;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 物流事件监听器 — 仅在 rabbitmq.enabled=true 时激活。
 * <p>
 * 降级方案：rabbitmq.enabled=false 时，物流记录通过 trade-service
 * → logistics-service 的同步 Feign 调用创建。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "rabbitmq.enabled", havingValue = "true")
public class LogisticsEventListener {

    private final LogisticsService logisticsService;

    @RabbitListener(queues = RabbitMqConfig.QUEUE_ORDER_SHIPPED)
    public void onOrderShipped(OrderShippedEvent event) {
        log.info("Received OrderShippedEvent: orderNo={}, logisticsNumber={}",
                event.getOrderNo(), event.getLogisticsNumber());
        try {
            OrderLogisticsEntity entity = new OrderLogisticsEntity();
            entity.setOrderId(event.getOrderId());
            entity.setLogisticsNumber(event.getLogisticsNumber());
            entity.setLogisticsCompany(event.getLogisticsCompany());
            entity.setContact(event.getContact());
            entity.setMobile(event.getMobile());
            logisticsService.createLogistics(entity);
            log.info("Logistics record auto-created via MQ for order: {}", event.getOrderNo());
        } catch (Exception e) {
            log.error("Failed to auto-create logistics for order: {}", event.getOrderNo(), e);
        }
    }
}
