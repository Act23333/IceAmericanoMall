package org.icedamericanomall.controller;

import lombok.RequiredArgsConstructor;
import org.icedamericanomall.domain.entity.OrderLogisticsEntity;
import org.icedamericanomall.service.LogisticsService;
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
