package org.icedAmericanoMall.controller;

import lombok.RequiredArgsConstructor;
import org.icedAmericanoMall.domain.entity.OrderLogisticsEntity;
import org.icedAmericanoMall.dto.CreateLogisticsDTO;
import org.icedAmericanoMall.service.LogisticsService;
import org.springframework.web.bind.annotation.*;

/**
 * Internal Feign endpoints — called by trade-service after order ships.
 */
@RestController
@RequestMapping("/internal/logistics")
@RequiredArgsConstructor
public class InternalLogisticsController {

    private final LogisticsService logisticsService;

    /**
     * Create logistics record after seller ships order.
     */
    @PostMapping("/create")
    public void createLogistics(@RequestBody CreateLogisticsDTO dto) {
        OrderLogisticsEntity entity = new OrderLogisticsEntity();
        entity.setOrderId(dto.getOrderId());
        entity.setLogisticsNumber(dto.getLogisticsNumber());
        entity.setLogisticsCompany(dto.getLogisticsCompany());
        entity.setContact(dto.getContact());
        entity.setMobile(dto.getMobile());
        entity.setProvince(dto.getProvince());
        entity.setCity(dto.getCity());
        entity.setDistrict(dto.getDistrict());
        entity.setStreet(dto.getStreet());
        entity.setDetail(dto.getDetail());
        logisticsService.createLogistics(entity);
    }

    /**
     * Update logistics status (called by scheduled task or courier callback).
     */
    @PutMapping("/{orderId}/status")
    public void updateStatus(@PathVariable Long orderId, @RequestParam Integer status) {
        logisticsService.updateStatus(orderId, status);
    }
}
