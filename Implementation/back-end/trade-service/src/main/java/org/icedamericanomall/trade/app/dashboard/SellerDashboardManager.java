package org.icedamericanomall.trade.app.dashboard;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.trade.domain.enums.OrderStatusEnum;
import org.icedamericanomall.trade.domain.model.order.Order;
import org.icedamericanomall.trade.domain.model.order.OrderItem;
import org.icedamericanomall.trade.domain.repository.OrderRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 商家看板编排 Manager —— 承接原 SellerDashboardController 中的聚合逻辑（Controller 只做参数校验）。
 *
 * <pre>
 * Scenario: 商家查看看板
 *   Then 返回今日订单数、待发货数、本月营收（分）
 * Scenario: 商家查看商品维度统计
 *   Then 按商品聚合最近 N 天销量与金额（一次 IN 查询批量取订单项，消除 N+1）
 * </pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SellerDashboardManager {

    private static final String KEY_TODAY_ORDER_COUNT = "todayOrderCount";
    private static final String KEY_PENDING_SHIP_COUNT = "pendingShipCount";
    private static final String KEY_MONTHLY_REVENUE = "monthlyRevenue";
    /**
     * 统计行的 SPU 名称键 —— 线格式键名收口为 {@code spu_name}
     * （ADR-056 snake_case + ADR-061 §决策 5 词汇；ADR-062 §后果 E2 随本批闭合）。
     */
    private static final String KEY_SPU_NAME = "spu_name";
    private static final String KEY_QUANTITY = "quantity";
    private static final String KEY_REVENUE = "revenue";

    private final OrderRepository orderRepository;

    /** 商家看板：今日订单数、待发货数、本月营收（已完成订单原价合计，单位分）。 */
    public Map<String, Object> dashboard(Long sellerId) {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        long todayOrderCount = orderRepository.countBySellerAndCreateTimeAfter(sellerId, todayStart);

        long pendingShipCount = orderRepository.countBySellerAndStatus(sellerId,
                OrderStatusEnum.PENDING_SHIPMENT.getCode());

        LocalDateTime monthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        int monthlyRevenue = sumAmount(orderRepository.findBySellerAndStatusAndCreateTimeAfter(
                sellerId, OrderStatusEnum.COMPLETED.getCode(), monthStart));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put(KEY_TODAY_ORDER_COUNT, todayOrderCount);
        result.put(KEY_PENDING_SHIP_COUNT, pendingShipCount);
        result.put(KEY_MONTHLY_REVENUE, monthlyRevenue); // in cents
        return result;
    }

    /**
     * V2.5 商品维度销量/金额统计（最近 N 天，按金额倒序）。
     *
     * <p>返回行的 SPU 名称键为 <b>{@code spu_name}</b>（ADR-056 snake_case +
     * ADR-061 §决策 5；ADR-062 §后果 E2 收口，原 camelCase {@code productName} 为 BREAKING）。
     */
    public List<Map<String, Object>> spuStats(Long sellerId, int days) {
        LocalDateTime since = LocalDate.now().minusDays(days).atStartOfDay();
        List<Order> orders = orderRepository.findBySellerAndCreateTimeAfter(sellerId, since);
        if (orders.isEmpty()) {
            return List.of();
        }
        List<Long> orderIds = orders.stream().map(Order::getId).toList();
        Map<String, int[]> agg = new LinkedHashMap<>();
        for (OrderItem item : orderRepository.findItemsByOrderIds(orderIds)) {
            agg.merge(item.getSpuName(),
                    new int[]{item.getQuantity(), item.getSubTotal()},
                    (a, b) -> new int[]{a[0] + b[0], a[1] + b[1]});
        }
        return agg.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue()[1], a.getValue()[1]))
                .map(e -> Map.<String, Object>of(KEY_SPU_NAME, e.getKey(),
                        KEY_QUANTITY, e.getValue()[0], KEY_REVENUE, e.getValue()[1]))
                .toList();
    }

    private int sumAmount(List<Order> orders) {
        return orders.stream().mapToInt(o -> o.getTotalAmount() != null ? o.getTotalAmount() : 0).sum();
    }
}
