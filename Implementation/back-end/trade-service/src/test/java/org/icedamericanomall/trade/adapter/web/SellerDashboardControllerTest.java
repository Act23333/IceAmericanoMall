package org.icedamericanomall.trade.adapter.web;

import org.icedamericanomall.common.security.UserContext;
import org.icedamericanomall.common.security.UserInfo;
import org.icedamericanomall.trade.adapter.response.SellerDashboardVO;
import org.icedamericanomall.trade.adapter.response.SpuStatVO;
import org.icedamericanomall.trade.app.dashboard.SellerDashboardManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SellerDashboardController 适配层接线测试（批次 D：{@code Result<Map>} → 类型化 VO）。
 *
 * <p>断言面：① 身份取 {@code UserContext}（网关注入，不信任前端传参）；
 * ② 出参为强类型 VO（{@code data} 为对象/数组，不再是 Map 拼装）；
 * ③ 金额字段 {@code monthly_revenue} / {@code revenue} 原样透出（<b>单位为分</b>，不做换算）。
 *
 * <p>⚠️ 本测试走 {@code standaloneSetup}，**不加载** {@code SNAKE_CASE} 策略，故这里只钉
 * 字段值与其存在性；线上键的 snake_case 断言见
 * {@code org.icedamericanomall.trade.adapter.response.DashboardVOWireTest}。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SellerDashboardController — 类型化 VO 委托 / Result 包装")
class SellerDashboardControllerTest {

    private static final Long SELLER_ID = 9L;

    @Mock
    private SellerDashboardManager sellerDashboardManager;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new SellerDashboardController(sellerDashboardManager))
                .build();
        UserContext.setUser(UserInfo.builder().userId(SELLER_ID).build());
    }

    @AfterEach
    void tearDown() {
        UserContext.removeUser();
    }

    @Test
    @DisplayName("dashboard — 当前商家身份委托 Manager，返回 SellerDashboardVO（营收为分）")
    void shouldDelegateWithCurrentSeller_whenDashboard() throws Exception {
        SellerDashboardVO vo = new SellerDashboardVO();
        vo.setTodayOrderCount(3L);
        vo.setPendingShipCount(2L);
        vo.setMonthlyRevenue(12800);
        when(sellerDashboardManager.dashboard(SELLER_ID)).thenReturn(vo);

        mockMvc.perform(MockMvcRequestBuilders.get("/api/trade/seller/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.todayOrderCount").value(3))
                .andExpect(jsonPath("$.data.pendingShipCount").value(2))
                .andExpect(jsonPath("$.data.monthlyRevenue").value(12800));

        verify(sellerDashboardManager).dashboard(eq(SELLER_ID));
    }

    @Test
    @DisplayName("product-stats — days 透传，返回 List<SpuStatVO>（spu_name / quantity / revenue）")
    void shouldReturnSpuStatVOs_whenProductStats() throws Exception {
        SpuStatVO row = new SpuStatVO();
        row.setSpuName("冰美式·大杯");
        row.setQuantity(7);
        row.setRevenue(25600);
        when(sellerDashboardManager.spuStats(SELLER_ID, 30)).thenReturn(List.of(row));

        mockMvc.perform(MockMvcRequestBuilders.get("/api/trade/seller/dashboard/product-stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].spuName").value("冰美式·大杯"))
                .andExpect(jsonPath("$.data[0].quantity").value(7))
                .andExpect(jsonPath("$.data[0].revenue").value(25600));

        verify(sellerDashboardManager).spuStats(eq(SELLER_ID), eq(30));
    }
}
