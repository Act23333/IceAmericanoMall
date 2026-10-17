package org.icedamericanomall.trade.app.dashboard;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.trade.adapter.response.SellerDashboardVO;
import org.icedamericanomall.trade.adapter.response.SpuStatVO;
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
 *
 * <p><b>批次 D（{@code Result<Map>} → 类型化 VO）</b>：两个用例的返回值分别由
 * {@code Map<String,Object>} / {@code List<Map<String,Object>>} 改为
 * {@link SellerDashboardVO} / {@code List<SpuStatVO>} —— <b>纯类型重构</b>：聚合算法、
 * 查询次数、排序键与数值口径<b>均未改动</b>。改造前散落的字面键常量
 * （{@code todayOrderCount} 等 camelCase 键、{@code spu_name}/{@code quantity}/{@code revenue}）
 * 随之删除 —— 线上键统一由 {@code SNAKE_CASE} 策略 + VO 字段名决定（ADR-056 / 契约 §1.8④）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SellerDashboardManager {

    private final OrderRepository orderRepository;

    /**
     * 商家看板：今日订单数、待发货数、本月营收（已完成订单原价合计，<b>单位分</b>）。
     *
     * @param sellerId 商家标识（{@code seller.user_id}，与 {@code UserContext.getUserId()} 同源）
     * @return 看板视图对象（各字段口径与单位见 {@link SellerDashboardVO}）
     */
    public SellerDashboardVO dashboard(Long sellerId) {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        long todayOrderCount = orderRepository.countBySellerAndCreateTimeAfter(sellerId, todayStart);

        long pendingShipCount = orderRepository.countBySellerAndStatus(sellerId,
                OrderStatusEnum.PENDING_SHIPMENT.getCode());

        LocalDateTime monthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        int monthlyRevenue = sumAmount(orderRepository.findBySellerAndStatusAndCreateTimeAfter(
                sellerId, OrderStatusEnum.COMPLETED.getCode(), monthStart));

        SellerDashboardVO vo = new SellerDashboardVO();
        vo.setTodayOrderCount(todayOrderCount);
        vo.setPendingShipCount(pendingShipCount);
        vo.setMonthlyRevenue(monthlyRevenue);
        return vo;
    }

    /**
     * V2.5 商品维度销量/金额统计（最近 N 天，按金额倒序）。
     *
     * <p>返回行的 SPU 名称字段为 {@code spuName}，线上键恒为 <b>{@code spu_name}</b>
     * （ADR-056 snake_case + ADR-061 §决策 5；ADR-062 §后果 E2 收口，原 camelCase
     * {@code productName} 为 BREAKING）。聚合键是 {@code order_item.spu_name} <b>快照</b>
     * ——同名快照合并、改名快照拆行的既有口径见 {@link SpuStatVO#getSpuName()}。
     *
     * <p>⚠️ <b>两条既有口径（本批只换类型、未改算法，已在交付报告登记）</b>：
     * <ol>
     *   <li><b>取数不限订单状态</b>（含已取消订单的订单项），与 AD14「仅已完成」不同源 ——
     *       见 {@link SpuStatVO#getRevenue()}；</li>
     *   <li><b>{@code days} 实际覆盖 {@code days + 1} 个自然日</b>（起点 = 今日零点减 {@code days} 天，
     *       含今日），而 AD13 {@code /stats/trend} 的 {@code days} 恰好返回 {@code days} 行 ——
     *       两端对同名参数的语义不一致。</li>
     * </ol>
     *
     * @param sellerId 商家标识（{@code seller.user_id}）
     * @param days     统计窗口天数（实际覆盖 {@code days + 1} 个自然日，含今日）
     * @return 统计行列表（金额倒序；无数据返回空列表，不返回 {@code null}）
     */
    public List<SpuStatVO> spuStats(Long sellerId, int days) {
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
                .map(e -> toSpuStatVO(e.getKey(), e.getValue()[0], e.getValue()[1]))
                .toList();
    }

    /** 统计行装配（入参 → VO；{@code revenue} 为分）。 */
    private SpuStatVO toSpuStatVO(String spuName, int quantity, int revenue) {
        SpuStatVO vo = new SpuStatVO();
        vo.setSpuName(spuName);
        vo.setQuantity(quantity);
        vo.setRevenue(revenue);
        return vo;
    }

    private int sumAmount(List<Order> orders) {
        return orders.stream().mapToInt(o -> o.getTotalAmount() != null ? o.getTotalAmount() : 0).sum();
    }
}
