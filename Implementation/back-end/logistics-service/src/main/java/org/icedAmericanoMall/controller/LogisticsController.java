package org.icedAmericanoMall.controller;

import lombok.RequiredArgsConstructor;
import org.icedAmericanoMall.domain.entity.OrderLogisticsEntity;
import org.icedAmericanoMall.service.LogisticsService;
import org.noLazy.common.domain.Result;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/logistics")
@RequiredArgsConstructor
public class LogisticsController {

    private final LogisticsService logisticsService;

    @GetMapping("/{orderId}")
    public Result<OrderLogisticsEntity> get(@PathVariable Long orderId) {
        return Result.ok(logisticsService.getByOrderId(orderId));
    }
}
