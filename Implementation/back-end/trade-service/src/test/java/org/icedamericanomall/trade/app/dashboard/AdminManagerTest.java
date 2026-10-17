package org.icedamericanomall.trade.app.dashboard;

import org.icedamericanomall.client.UserClient;
import org.icedamericanomall.trade.adapter.response.AdminDashboardVO;
import org.icedamericanomall.trade.adapter.response.OrderTrendVO;
import org.icedamericanomall.trade.adapter.response.SalesByDateVO;
import org.icedamericanomall.trade.adapter.response.SpuStatVO;
import org.icedamericanomall.trade.domain.enums.OrderStatusEnum;
import org.icedamericanomall.trade.domain.model.order.Order;
import org.icedamericanomall.trade.domain.model.order.OrderItem;
import org.icedamericanomall.trade.domain.repository.OrderRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * AdminManager 单元测试（批次 D：{@code Result<Map>} → 类型化 VO）。
 *
 * <p>与 {@link SellerDashboardManagerTest} 同一定位：证明四个统计用例在换类型后
 * <b>数值与形状行为完全不变</b> —— 尤其「趋势按日期升序、天数 = 入参」「区间左闭右闭、
 * 逐日枚举」「金额为分」这几条容易被「顺手重构」改坏的既有语义。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AdminManager — 类型化 VO 后数值与形状不变")
class AdminManagerTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private UserClient userClient;

    @InjectMocks
    private AdminManager adminManager;

    @Test
    @DisplayName("dashboard — 6 个指标逐一透出；用户数跨服务失败 fail-open 为 0")
    void shouldReturnSixMetrics_whenDashboard() {
        when(userClient.countUsers()).thenThrow(new RuntimeException("user-service down"));
        when(orderRepository.countAll()).thenReturn(120L);
        when(orderRepository.countByStatus(eq(OrderStatusEnum.COMPLETED.getCode()))).thenReturn(80L);
        when(orderRepository.findByStatus(eq(OrderStatusEnum.COMPLETED.getCode())))
                .thenReturn(List.of(orderWithTotal(10000), orderWithTotal(20000)));
        when(orderRepository.countByCreateTimeAfter(any(LocalDateTime.class))).thenReturn(5L);
        when(orderRepository.findByStatusAndCreateTimeAfter(eq(OrderStatusEnum.COMPLETED.getCode()),
                any(LocalDateTime.class))).thenReturn(List.of(orderWithTotal(3000)));

        AdminDashboardVO vo = adminManager.dashboard();

        assertEquals(0L, vo.getTotalUsers());
        assertEquals(120L, vo.getTotalOrders());
        assertEquals(80L, vo.getCompletedOrders());
        assertEquals(30000, vo.getTotalRevenue());
        assertEquals(5L, vo.getTodayOrders());
        assertEquals(3000, vo.getTodayRevenue());
    }

    @Test
    @DisplayName("orderTrend — 行数 = days，日期升序（最早在前，今日最后）")
    void shouldReturnAscendingDailyRows_whenOrderTrend() {
        when(orderRepository.countByCreateTimeBetween(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(2L);
        when(orderRepository.findByStatusAndCreateTimeBetween(eq(OrderStatusEnum.COMPLETED.getCode()),
                any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(orderWithTotal(1500)));

        List<OrderTrendVO> rows = adminManager.orderTrend(3);

        assertEquals(3, rows.size());
        assertEquals(LocalDate.now().minusDays(2).toString(), rows.get(0).getDate());
        assertEquals(LocalDate.now().minusDays(1).toString(), rows.get(1).getDate());
        assertEquals(LocalDate.now().toString(), rows.get(2).getDate());
        assertEquals(2L, rows.get(0).getOrders());
        assertEquals(1500, rows.get(0).getRevenue());
    }

    @Test
    @DisplayName("saleByDateRange — 区间左闭右闭逐日枚举（from=to 时恰好 1 行）")
    void shouldEnumerateInclusiveRange_whenSalesByDateRange() {
        LocalDate day = LocalDate.of(2026, 10, 18);
        when(orderRepository.countByCreateTimeBetween(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(4L);
        when(orderRepository.findByStatusAndCreateTimeBetween(eq(OrderStatusEnum.COMPLETED.getCode()),
                any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(orderWithTotal(2000), orderWithTotal(500)));

        List<SalesByDateVO> sameDay = adminManager.saleByDateRange(day, day);
        assertEquals(1, sameDay.size());
        assertEquals("2026-10-18", sameDay.get(0).getDate());
        assertEquals(4L, sameDay.get(0).getOrders());
        assertEquals(2500, sameDay.get(0).getRevenue());

        assertEquals(3, adminManager.saleByDateRange(day, day.plusDays(2)).size());
    }

    @Test
    @DisplayName("topSpus — 按销量倒序取前 limit，元素为 SpuStatVO（金额为分）")
    void shouldSortByQuantityDesc_whenTopSpus() {
        Order first = orderWithTotal(0);
        first.setId(1L);
        Order second = orderWithTotal(0);
        second.setId(2L);
        when(orderRepository.findByStatus(eq(OrderStatusEnum.COMPLETED.getCode())))
                .thenReturn(List.of(first, second));
        when(orderRepository.findItemsByOrderId(eq(1L)))
                .thenReturn(List.of(item("冰美式", 1, 1000)));
        when(orderRepository.findItemsByOrderId(eq(2L)))
                .thenReturn(List.of(item("拿铁", 9, 9000)));

        List<SpuStatVO> rows = adminManager.topSpus(1);

        assertEquals(1, rows.size());
        assertEquals("拿铁", rows.get(0).getSpuName());
        assertEquals(9, rows.get(0).getQuantity());
        assertEquals(9000, rows.get(0).getRevenue());
    }

    @Test
    @DisplayName("topSpus — limit 为 0 / 无已完成订单 → 空列表")
    void shouldReturnEmpty_whenNoCompletedOrders() {
        when(orderRepository.findByStatus(eq(OrderStatusEnum.COMPLETED.getCode()))).thenReturn(List.of());

        assertTrue(adminManager.topSpus(10).isEmpty());
    }

    private static Order orderWithTotal(int totalAmount) {
        Order order = new Order();
        order.setTotalAmount(totalAmount);
        return order;
    }

    private static OrderItem item(String spuName, int quantity, int subTotal) {
        OrderItem item = new OrderItem();
        item.setSpuName(spuName);
        item.setQuantity(quantity);
        item.setSubTotal(subTotal);
        return item;
    }
}
