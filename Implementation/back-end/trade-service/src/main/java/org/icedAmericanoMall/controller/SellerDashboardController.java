package org.icedAmericanoMall.controller;

import lombok.RequiredArgsConstructor;
import org.icedAmericanoMall.domain.entity.OrderEntity;
import org.icedAmericanoMall.enums.OrderStatusEnum;
import org.icedAmericanoMall.service.OrderService;
import org.noLazy.common.domain.Result;
import org.noLazy.common.utils.UserContext;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Seller dashboard — simple statistics for the seller view.
 */
@RestController
@RequestMapping("/api/trade/seller/dashboard")
@RequiredArgsConstructor
public class SellerDashboardController {

    private final OrderService orderService;

    @GetMapping
    public Result<Map<String, Object>> dashboard() {
        Long sellerId = UserContext.getUser();

        // Today's stats
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        long todayOrderCount = orderService.lambdaQuery()
                .eq(OrderEntity::getSellerId, sellerId)
                .ge(OrderEntity::getCreateTime, todayStart)
                .count();

        long pendingShipCount = orderService.lambdaQuery()
                .eq(OrderEntity::getSellerId, sellerId)
                .eq(OrderEntity::getStatus, OrderStatusEnum.PENDING_SHIPMENT.getCode())
                .count();

        // This month's revenue (completed orders only, in cents)
        LocalDateTime monthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        var monthOrders = orderService.lambdaQuery()
                .eq(OrderEntity::getSellerId, sellerId)
                .eq(OrderEntity::getStatus, OrderStatusEnum.COMPLETED.getCode())
                .ge(OrderEntity::getCreateTime, monthStart)
                .list();
        int monthlyRevenue = monthOrders.stream().mapToInt(o -> o.getTotalAmount() != null ? o.getTotalAmount() : 0).sum();

        Map<String, Object> result = new HashMap<>();
        result.put("todayOrderCount", todayOrderCount);
        result.put("pendingShipCount", pendingShipCount);
        result.put("monthlyRevenue", monthlyRevenue); // in cents
        return Result.ok(result);
    }
}
