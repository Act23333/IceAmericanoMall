package org.icedamericanomall.trade.adapter.admin;

import org.icedamericanomall.trade.adapter.response.AdminDashboardVO;
import org.icedamericanomall.trade.adapter.response.OrderTrendVO;
import org.icedamericanomall.trade.adapter.response.SalesByDateVO;
import org.icedamericanomall.trade.adapter.response.SpuStatVO;
import org.icedamericanomall.trade.app.dashboard.AdminManager;
import org.icedamericanomall.trade.app.order.OrderQueryManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AdminOrderController 四个统计端点接线测试（批次 D：{@code Result<Map>} → 类型化 VO）。
 *
 * <p>覆盖 AD11（仪表盘）、AD13（趋势）、AD14（Top 商品）、AD15（区间分日销售额）。
 * 断言面 = 委托 + 强类型出参 + 金额原样透出（<b>分</b>）。线上键的 snake_case 断言见
 * {@code org.icedamericanomall.trade.adapter.response.DashboardVOWireTest}（本测试走
 * {@code standaloneSetup}，不加载 SNAKE_CASE 策略）。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AdminOrderController — 统计端点类型化 VO 委托")
class AdminOrderControllerTest {

    @Mock
    private AdminManager adminManager;

    @Mock
    private OrderQueryManager orderQueryManager;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AdminOrderController(adminManager, orderQueryManager))
                .build();
    }

    @Test
    @DisplayName("dashboard — 返回 AdminDashboardVO（累计/今日营收为分）")
    void shouldReturnDashboardVO() throws Exception {
        AdminDashboardVO vo = new AdminDashboardVO();
        vo.setTotalUsers(12580L);
        vo.setTotalOrders(45620L);
        vo.setCompletedOrders(1000L);
        vo.setTotalRevenue(892450000);
        vo.setTodayOrders(356L);
        vo.setTodayRevenue(5680000);
        when(adminManager.dashboard()).thenReturn(vo);

        mockMvc.perform(MockMvcRequestBuilders.get("/api/trade/admin/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalUsers").value(12580))
                .andExpect(jsonPath("$.data.completedOrders").value(1000))
                .andExpect(jsonPath("$.data.totalRevenue").value(892450000))
                .andExpect(jsonPath("$.data.todayRevenue").value(5680000));
    }

    @Test
    @DisplayName("stats/trend — days 透传，返回 List<OrderTrendVO>")
    void shouldReturnTrendRows_whenDaysGiven() throws Exception {
        OrderTrendVO row = new OrderTrendVO();
        row.setDate("2026-10-18");
        row.setOrders(12L);
        row.setRevenue(34000);
        when(adminManager.orderTrend(7)).thenReturn(List.of(row));

        mockMvc.perform(MockMvcRequestBuilders.get("/api/trade/admin/stats/trend").param("days", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].date").value("2026-10-18"))
                .andExpect(jsonPath("$.data[0].orders").value(12))
                .andExpect(jsonPath("$.data[0].revenue").value(34000));

        verify(adminManager).orderTrend(eq(7));
    }

    @Test
    @DisplayName("stats/top-products — limit 透传，返回 List<SpuStatVO>（与 S11 同型）")
    void shouldReturnSpuStatRows_whenLimitGiven() throws Exception {
        SpuStatVO row = new SpuStatVO();
        row.setSpuName("冰美式·大杯");
        row.setQuantity(9);
        row.setRevenue(9000);
        when(adminManager.topSpus(5)).thenReturn(List.of(row));

        mockMvc.perform(MockMvcRequestBuilders.get("/api/trade/admin/stats/top-products").param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].spuName").value("冰美式·大杯"))
                .andExpect(jsonPath("$.data[0].revenue").value(9000));

        verify(adminManager).topSpus(eq(5));
    }

    @Test
    @DisplayName("stats/sales — from/to 解析为 LocalDate 透传，返回 List<SalesByDateVO>")
    void shouldParseDateRange_whenSalesQueried() throws Exception {
        SalesByDateVO row = new SalesByDateVO();
        row.setDate("2026-10-18");
        row.setOrders(4L);
        row.setRevenue(2500);
        when(adminManager.saleByDateRange(LocalDate.of(2026, 10, 18), LocalDate.of(2026, 10, 19)))
                .thenReturn(List.of(row));

        mockMvc.perform(MockMvcRequestBuilders.get("/api/trade/admin/stats/sales")
                        .param("from", "2026-10-18").param("to", "2026-10-19"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].date").value("2026-10-18"))
                .andExpect(jsonPath("$.data[0].orders").value(4))
                .andExpect(jsonPath("$.data[0].revenue").value(2500));

        verify(adminManager).saleByDateRange(eq(LocalDate.of(2026, 10, 18)), eq(LocalDate.of(2026, 10, 19)));
    }
}
