package org.icedamericanomall.manager;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.client.UserClient;
import org.icedamericanomall.domain.entity.OrderEntity;
import org.icedamericanomall.enums.OrderStatusEnum;
import org.icedamericanomall.service.OrderService;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理后台统计编排 Manager —— 聚合订单指标与跨服务用户数（Feign），供仪表盘/趋势图使用。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminManager {

    private final OrderService orderService;
    private final UserClient userClient;

    /** 仪表盘：用户数、订单数、营收（分）。 */
    public Map<String, Object> dashboard() {
        long totalUsers = 0;
        try {
            Long count = userClient.countUsers();
            totalUsers = count != null ? count : 0;
        } catch (Exception e) {
            log.warn("获取用户总数失败（user-service 不可用）: {}", e.getMessage());
        }

        long totalOrders = orderService.lambdaQuery().count();
        int completedCode = OrderStatusEnum.COMPLETED.getCode();
        long completedOrders = orderService.lambdaQuery().eq(OrderEntity::getStatus, completedCode).count();
        int totalRevenue = sumRevenue(orderService.lambdaQuery()
                .eq(OrderEntity::getStatus, completedCode).list());

        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        long todayOrders = orderService.lambdaQuery().ge(OrderEntity::getCreateTime, todayStart).count();
        int todayRevenue = sumRevenue(orderService.lambdaQuery()
                .eq(OrderEntity::getStatus, completedCode)
                .ge(OrderEntity::getCreateTime, todayStart).list());

        Map<String, Object> result = new HashMap<>();
        result.put("totalUsers", totalUsers);
        result.put("totalOrders", totalOrders);
        result.put("completedOrders", completedOrders);
        result.put("totalRevenue", totalRevenue);
        result.put("todayOrders", todayOrders);
        result.put("todayRevenue", todayRevenue);
        return result;
    }

    /** 近 N 天订单趋势：每日订单数 + 营收。 */
    public List<Map<String, Object>> orderTrend(int days) {
        int completedCode = OrderStatusEnum.COMPLETED.getCode();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = days - 1; i >= 0; i--) {
            LocalDate date = LocalDate.now().minusDays(i);
            LocalDateTime start = date.atStartOfDay();
            LocalDateTime end = date.plusDays(1).atStartOfDay();

            long count = orderService.lambdaQuery()
                    .ge(OrderEntity::getCreateTime, start)
                    .lt(OrderEntity::getCreateTime, end).count();
            int revenue = sumRevenue(orderService.lambdaQuery()
                    .eq(OrderEntity::getStatus, completedCode)
                    .ge(OrderEntity::getCreateTime, start)
                    .lt(OrderEntity::getCreateTime, end).list());

            rows.add(Map.of("date", date.toString(), "orders", count, "revenue", revenue));
        }
        return rows;
    }

    private int sumRevenue(List<OrderEntity> orders) {
        return orders.stream().mapToInt(o -> o.getTotalAmount() != null ? o.getTotalAmount() : 0).sum();
    }

    /** 销量 Top N（V2.5 详细统计）。 */
    public List<Map<String, Object>> topProducts(int limit) {
        int completedCode = OrderStatusEnum.COMPLETED.getCode();
        return orderService.lambdaQuery().eq(OrderEntity::getStatus, completedCode).list()
                .stream()
                .flatMap(o -> orderService.listItems(o.getId()).stream()
                        .map(i -> new Object[]{i.getProductName(), i.getQuantity(), i.getSubTotal()}))
                .collect(java.util.stream.Collectors.groupingBy(
                        a -> (String) a[0],
                        java.util.stream.Collectors.reducing(
                                new int[]{0, 0},
                                a -> new int[]{(Integer) a[1], (Integer) a[2]},
                                (a, b) -> new int[]{a[0] + b[0], a[1] + b[1]})))
                .entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue()[0], a.getValue()[0]))
                .limit(limit)
                .map(e -> Map.<String, Object>of("productName", e.getKey(),
                        "quantity", e.getValue()[0], "revenue", e.getValue()[1]))
                .collect(java.util.stream.Collectors.toList());
    }

    /** 分日销售额（V2.5 详细统计）。 */
    public List<Map<String, Object>> saleByDateRange(LocalDate from, LocalDate to) {
        int completedCode = OrderStatusEnum.COMPLETED.getCode();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            LocalDateTime start = d.atStartOfDay();
            LocalDateTime end = d.plusDays(1).atStartOfDay();
            long count = orderService.lambdaQuery()
                    .ge(OrderEntity::getCreateTime, start).lt(OrderEntity::getCreateTime, end).count();
            int revenue = sumRevenue(orderService.lambdaQuery()
                    .eq(OrderEntity::getStatus, completedCode)
                    .ge(OrderEntity::getCreateTime, start).lt(OrderEntity::getCreateTime, end).list());
            rows.add(Map.of("date", d.toString(), "orders", count, "revenue", revenue));
        }
        return rows;
    }
}
