package org.icedamericanomall.trade.app.dashboard;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.client.UserClient;
import org.icedamericanomall.trade.adapter.response.AdminDashboardVO;
import org.icedamericanomall.trade.adapter.response.OrderTrendVO;
import org.icedamericanomall.trade.adapter.response.SalesByDateVO;
import org.icedamericanomall.trade.adapter.response.SpuStatVO;
import org.icedamericanomall.trade.domain.enums.OrderStatusEnum;
import org.icedamericanomall.trade.domain.model.order.Order;
import org.icedamericanomall.trade.domain.repository.OrderRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 管理后台统计编排 Manager —— 聚合订单指标与跨服务用户数（Feign），供仪表盘/趋势图使用。
 *
 * <p><b>批次 D（{@code Result<Map>} → 类型化 VO）</b>：四个用例的返回值由弱类型 Map
 * （{@code Map<String,Object>} / {@code List<Map<String,Object>>}）改为
 * {@link AdminDashboardVO} / {@code List<OrderTrendVO>} / {@code List<SpuStatVO>} /
 * {@code List<SalesByDateVO>} —— <b>纯类型重构</b>：指标口径、查询次数与排序/limit 行为
 * 均<b>未改动</b>。改造前 {@code HashMap} 插入顺序与 {@code Map.of} 字面键不再承担契约职责，
 * 线上键由 {@code SNAKE_CASE} 策略 + VO 字段名决定（ADR-056）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminManager {

    private final OrderRepository orderRepository;
    private final UserClient userClient;

    /**
     * 仪表盘：用户数、订单数、营收（<b>分</b>）。
     *
     * @return 仪表盘视图对象（各字段口径与单位见 {@link AdminDashboardVO}）
     */
    public AdminDashboardVO dashboard() {
        long totalUsers = 0;
        try {
            Long count = userClient.countUsers();
            totalUsers = count != null ? count : 0;
        } catch (Exception e) {
            log.warn("获取用户总数失败（user-service 不可用）: {}", e.getMessage());
        }

        int completedCode = OrderStatusEnum.COMPLETED.getCode();
        long totalOrders = orderRepository.countAll();
        long completedOrders = orderRepository.countByStatus(completedCode);
        int totalRevenue = sumRevenue(orderRepository.findByStatus(completedCode));

        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        long todayOrders = orderRepository.countByCreateTimeAfter(todayStart);
        int todayRevenue = sumRevenue(orderRepository.findByStatusAndCreateTimeAfter(completedCode, todayStart));

        AdminDashboardVO vo = new AdminDashboardVO();
        vo.setTotalUsers(totalUsers);
        vo.setTotalOrders(totalOrders);
        vo.setCompletedOrders(completedOrders);
        vo.setTotalRevenue(totalRevenue);
        vo.setTodayOrders(todayOrders);
        vo.setTodayRevenue(todayRevenue);
        return vo;
    }

    /**
     * 近 N 天订单趋势：每日订单数 + 营收（<b>分</b>；日期为行内字段，非 Map 键）。
     *
     * @param days 窗口天数（自今日向前推，含今日；按日期升序返回）
     * @return 趋势数据点列表
     */
    public List<OrderTrendVO> orderTrend(int days) {
        int completedCode = OrderStatusEnum.COMPLETED.getCode();
        List<OrderTrendVO> rows = new ArrayList<>();
        for (int i = days - 1; i >= 0; i--) {
            LocalDate date = LocalDate.now().minusDays(i);
            LocalDateTime start = date.atStartOfDay();
            LocalDateTime end = date.plusDays(1).atStartOfDay();

            long count = orderRepository.countByCreateTimeBetween(start, end);
            int revenue = sumRevenue(orderRepository.findByStatusAndCreateTimeBetween(completedCode, start, end));

            OrderTrendVO row = new OrderTrendVO();
            row.setDate(date.toString());
            row.setOrders(count);
            row.setRevenue(revenue);
            rows.add(row);
        }
        return rows;
    }

    private int sumRevenue(List<Order> orders) {
        return orders.stream().mapToInt(o -> o.getTotalAmount() != null ? o.getTotalAmount() : 0).sum();
    }

    /**
     * 销量 Top N（V2.5 详细统计）。
     *
     * <p>返回行的 SPU 名称字段为 {@code spuName}，线上键恒为 <b>{@code spu_name}</b>
     * （ADR-056 snake_case + ADR-061 §决策 5；ADR-062 §后果 E2 收口，原 camelCase
     * {@code productName} 为 BREAKING）。响应元素类型 = {@link SpuStatVO}，与商家端
     * S11 {@code product-stats} <b>同型同义</b>（契约 AD14 早已声明 {@code List<SpuStatVO>}，
     * B4 因代码中无该类曾按实测回退为 Map；本批把类补齐，契约恢复原声明）。
     *
     * @param limit 返回行数上限（按销量倒序取前 N）
     * @return 统计行列表（{@code revenue} 为分）
     */
    public List<SpuStatVO> topSpus(int limit) {
        int completedCode = OrderStatusEnum.COMPLETED.getCode();
        return orderRepository.findByStatus(completedCode)
                .stream()
                .flatMap(o -> orderRepository.findItemsByOrderId(o.getId()).stream()
                        .map(i -> new Object[]{i.getSpuName(), i.getQuantity(), i.getSubTotal()}))
                .collect(java.util.stream.Collectors.groupingBy(
                        a -> (String) a[0],
                        java.util.stream.Collectors.reducing(
                                new int[]{0, 0},
                                a -> new int[]{(Integer) a[1], (Integer) a[2]},
                                (a, b) -> new int[]{a[0] + b[0], a[1] + b[1]})))
                .entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue()[0], a.getValue()[0]))
                .limit(limit)
                .map(e -> {
                    SpuStatVO vo = new SpuStatVO();
                    vo.setSpuName(e.getKey());
                    vo.setQuantity(e.getValue()[0]);
                    vo.setRevenue(e.getValue()[1]);
                    return vo;
                })
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * 分日销售额（V2.5 详细统计）。
     *
     * @param from 区间起（含）
     * @param to   区间止（含）
     * @return 分日数据点列表（{@code revenue} 为分；逐日枚举，含两端）
     */
    public List<SalesByDateVO> saleByDateRange(LocalDate from, LocalDate to) {
        int completedCode = OrderStatusEnum.COMPLETED.getCode();
        List<SalesByDateVO> rows = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            LocalDateTime start = d.atStartOfDay();
            LocalDateTime end = d.plusDays(1).atStartOfDay();
            long count = orderRepository.countByCreateTimeBetween(start, end);
            int revenue = sumRevenue(orderRepository.findByStatusAndCreateTimeBetween(completedCode, start, end));

            SalesByDateVO row = new SalesByDateVO();
            row.setDate(d.toString());
            row.setOrders(count);
            row.setRevenue(revenue);
            rows.add(row);
        }
        return rows;
    }
}
