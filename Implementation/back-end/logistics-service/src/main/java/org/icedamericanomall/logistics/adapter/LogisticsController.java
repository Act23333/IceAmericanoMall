package org.icedamericanomall.logistics.adapter;

import lombok.RequiredArgsConstructor;
import org.icedamericanomall.logistics.domain.entity.OrderLogisticsEntity;
import org.icedamericanomall.logistics.service.LogisticsService;
import org.icedamericanomall.common.domain.Result;
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
