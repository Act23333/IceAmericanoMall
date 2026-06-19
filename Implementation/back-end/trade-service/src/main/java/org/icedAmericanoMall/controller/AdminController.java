package org.icedAmericanoMall.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.RequiredArgsConstructor;
import org.icedAmericanoMall.client.UserClient;
import org.icedAmericanoMall.domain.entity.OrderEntity;
import org.icedAmericanoMall.enums.OrderStatusEnum;
import org.icedAmericanoMall.service.OrderService;
import org.noLazy.common.domain.Result;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 管理员后台 — 仪表盘 & 订单管理
 *
 * <pre>
 * Scenario: 管理员查看仪表盘
 *   Given 管理员已登录
 *   When GET /api/trade/admin/dashboard
 *   Then 返回 { totalUsers, totalOrders, totalRevenue, todayOrders, todayRevenue }
 *
 * Scenario: 管理员查看所有订单
 *   Given 管理员已登录
 *   When GET /api/trade/admin/orders/page?page=1&size=20&status=1
 *   Then 返回分页订单列表（不限用户/商家）
 * </pre>
 */
@RestController
@RequestMapping("/api/trade/admin")
@RequiredArgsConstructor
public class AdminController {

    private final OrderService orderService;
    private final UserClient userClient;

    /**
     * Admin dashboard: user count, order count, revenue stats.
     */
    @GetMapping("/dashboard")
    public Result<Map<String, Object>> dashboard() {
        // Total metrics
        long totalUsers = 0;
        try {
            Long count = userClient.countUsers();
            totalUsers = count != null ? count : 0;
        } catch (Exception e) {
            // Fallback if user-service unavailable
        }

        long totalOrders = orderService.lambdaQuery().count();
        long completedOrders = orderService.lambdaQuery()
                .eq(OrderEntity::getStatus, OrderStatusEnum.COMPLETED.getCode())
                .count();

        // Total revenue (completed orders, in cents)
        var completedList = orderService.lambdaQuery()
                .eq(OrderEntity::getStatus, OrderStatusEnum.COMPLETED.getCode())
                .list();
        int totalRevenue = completedList.stream()
                .mapToInt(o -> o.getTotalAmount() != null ? o.getTotalAmount() : 0)
                .sum();

        // Today's stats
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        long todayOrders = orderService.lambdaQuery()
                .ge(OrderEntity::getCreateTime, todayStart)
                .count();
        var todayList = orderService.lambdaQuery()
                .eq(OrderEntity::getStatus, OrderStatusEnum.COMPLETED.getCode())
                .ge(OrderEntity::getCreateTime, todayStart)
                .list();
        int todayRevenue = todayList.stream()
                .mapToInt(o -> o.getTotalAmount() != null ? o.getTotalAmount() : 0)
                .sum();

        Map<String, Object> result = new HashMap<>();
        result.put("totalUsers", totalUsers);
        result.put("totalOrders", totalOrders);
        result.put("completedOrders", completedOrders);
        result.put("totalRevenue", totalRevenue);       // in cents
        result.put("todayOrders", todayOrders);
        result.put("todayRevenue", todayRevenue);       // in cents
        return Result.ok(result);
    }

    /**
     * Admin: paginated list of all orders across all users/sellers.
     */
    @GetMapping("/orders/page")
    public Result<IPage<OrderEntity>> pageAllOrders(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Integer status) {
        return Result.ok(orderService.pageAllOrders(status, page, size));
    }

    /**
     * V1.2: 30-day order trend — daily count + revenue for chart.
     */
    @GetMapping("/stats/trend")
    public Result<?> trend(@RequestParam(defaultValue = "30") int days) {
        var rows = new java.util.ArrayList<Map<String, Object>>();
        for (int i = days - 1; i >= 0; i--) {
            LocalDate date = LocalDate.now().minusDays(i);
            LocalDateTime start = date.atStartOfDay();
            LocalDateTime end = date.plusDays(1).atStartOfDay();

            long count = orderService.lambdaQuery()
                    .ge(OrderEntity::getCreateTime, start)
                    .lt(OrderEntity::getCreateTime, end).count();

            var list = orderService.lambdaQuery()
                    .eq(OrderEntity::getStatus, OrderStatusEnum.COMPLETED.getCode())
                    .ge(OrderEntity::getCreateTime, start)
                    .lt(OrderEntity::getCreateTime, end).list();
            int revenue = list.stream().mapToInt(o -> o.getTotalAmount() != null ? o.getTotalAmount() : 0).sum();

            rows.add(Map.of("date", date.toString(), "orders", count, "revenue", revenue));
        }
        return Result.ok(rows);
    }
}
