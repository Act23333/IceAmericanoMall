package org.icedamericanomall.manager;

import lombok.RequiredArgsConstructor;
import org.icedamericanomall.domain.entity.OrderLogisticsEntity;
import org.icedamericanomall.service.LogisticsService;
import org.springframework.stereotype.Component;

/**
 * V4.0 DDD: 物流编排 Manager（Application层）。
 * 职责: 物流CRUD编排。
 */
@Component
@RequiredArgsConstructor
public class LogisticsManager {

    private final LogisticsService logisticsService;

    public OrderLogisticsEntity getByOrderId(Long orderId) {
        return logisticsService.getByOrderId(orderId);
    }

    public void createLogistics(OrderLogisticsEntity entity) {
        logisticsService.createLogistics(entity);
    }

    public void updateStatus(Long orderId, Integer status) {
        logisticsService.updateStatus(orderId, status);
    }
}
