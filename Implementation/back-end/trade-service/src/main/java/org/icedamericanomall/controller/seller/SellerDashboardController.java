package org.icedamericanomall.controller.seller;

import lombok.RequiredArgsConstructor;
import org.icedamericanomall.domain.entity.OrderEntity;
import org.icedamericanomall.enums.OrderStatusEnum;
import org.icedamericanomall.service.OrderService;
import org.noLazy.common.domain.Result;
import org.noLazy.common.utils.UserContext;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

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
        Long sellerId = UserContext.getUserId();

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

    /** V2.5 商品维度销量/金额统计（最近 N 天）。 */
    @GetMapping("/product-stats")
    public Result<List<Map<String, Object>>> productStats(@RequestParam(defaultValue = "30") int days) {
        Long sellerId = UserContext.getUserId();
        LocalDateTime since = LocalDate.now().minusDays(days).atStartOfDay();
        List<OrderEntity> orders = orderService.lambdaQuery()
                .eq(OrderEntity::getSellerId, sellerId)
                .ge(OrderEntity::getCreateTime, since).list();
        Map<String, int[]> agg = new LinkedHashMap<>();
        for (OrderEntity o : orders) {
            orderService.listItems(o.getId()).forEach(i -> agg.merge(
                    i.getProductName(),
                    new int[]{i.getQuantity(), i.getSubTotal()},
                    (a, b) -> new int[]{a[0] + b[0], a[1] + b[1]}));
        }
        return Result.ok(agg.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue()[1], a.getValue()[1]))
                .map(e -> Map.<String, Object>of("productName", e.getKey(),
                        "quantity", e.getValue()[0], "revenue", e.getValue()[1]))
                .collect(Collectors.toList()));
    }
}
