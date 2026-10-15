package org.icedamericanomall.marketing.adapter.internal;

import org.icedamericanomall.common.aspect.GlobalExceptionHandler;
import org.icedamericanomall.dto.CouponStackInfoCO;
import org.icedamericanomall.marketing.app.coupon.CouponClaimManager;
import org.icedamericanomall.marketing.app.coupon.CouponUsageManager;
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

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * InternalCouponController 适配层接线测试（I69 {@code /batch} + {@code /use} 范围参数透传）。
 *
 * <p>控制器只做参数绑定与委托：{@code ids} 按逗号切分为 {@code user_coupon.id} 列表（与
 * {@code /use} 的 {@code user_coupon_id} 同口径），{@code product_ids}/{@code category_ids} 原样透传给
 * 应用层；{@code /internal/**} 不包 {@code Result}（ADR-004）。{@code /use} 的线上查询参数名
 * 一律 snake_case（{@code user_id}/{@code user_coupon_id}/{@code order_no}/{@code order_amount}/
 * {@code order_type}/{@code seller_id}/{@code product_ids}/{@code category_ids}），由
 * {@code @RequestParam(name = ...)} 显式钉死。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("InternalCouponController — I69 /batch 与 /use 范围参数")
class InternalCouponControllerTest {

    @Mock
    private CouponUsageManager couponUsageManager;

    @Mock
    private CouponClaimManager couponClaimManager;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new InternalCouponController(couponUsageManager, couponClaimManager))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("batch — ids 逗号分隔 → 委托 batchStackInfo 且解析为持券 ID 列表，裸列表透出")
    void shouldDelegateBatch_whenCommaSeparatedIds() throws Exception {
        when(couponUsageManager.batchStackInfo(List.of(501L, 502L)))
                .thenReturn(List.of(stackInfo(501L, "GROUP-A")));

        mockMvc.perform(MockMvcRequestBuilders.get("/internal/coupon/batch").param("ids", "501,502"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(501))
                .andExpect(jsonPath("$[0].stackGroup").value("GROUP-A"));

        verify(couponUsageManager).batchStackInfo(List.of(501L, 502L));
    }

    @Test
    @DisplayName("batch — 单值 ids → 单元素列表委托")
    void shouldDelegateBatch_whenSingleId() throws Exception {
        when(couponUsageManager.batchStackInfo(List.of(501L))).thenReturn(List.of());

        mockMvc.perform(MockMvcRequestBuilders.get("/internal/coupon/batch").param("ids", "501"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());

        verify(couponUsageManager).batchStackInfo(List.of(501L));
    }

    @Test
    @DisplayName("batch — 部分持券无模板被跳过 → 只透出命中项（fail-open，不报错）")
    void shouldReturnPartialList_whenSomeTemplatesMissing() throws Exception {
        when(couponUsageManager.batchStackInfo(List.of(501L, 502L)))
                .thenReturn(List.of(stackInfo(501L, "GROUP-B")));

        mockMvc.perform(MockMvcRequestBuilders.get("/internal/coupon/batch").param("ids", "501,502"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(501))
                .andExpect(jsonPath("$[0].stackRule").value(2));
    }

    @Test
    @DisplayName("use — snake 名绑定生效：product_ids/category_ids 缺省 → 透传 null（应用层据此判定限定券拒绝）")
    void shouldDelegateUseWithNullScopeParams_whenAbsent() throws Exception {
        when(couponUsageManager.useCoupon(100L, 55L, "ORD-1", 10000, 1, 9L, null, null))
                .thenReturn(1000);

        mockMvc.perform(MockMvcRequestBuilders.post("/internal/coupon/use")
                        .param("user_id", "100").param("user_coupon_id", "55").param("order_no", "ORD-1")
                        .param("order_amount", "10000").param("order_type", "1").param("seller_id", "9"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("use — snake 名绑定生效：product_ids/category_ids 原样透传（逗号分隔串不拆分）")
    void shouldDelegateUseWithScopeParams_whenPresent() throws Exception {
        when(couponUsageManager.useCoupon(eq(100L), eq(55L), eq("ORD-1"), eq(10000), eq(1), eq(9L),
                eq("1001,1002"), eq("7,8"))).thenReturn(0);

        mockMvc.perform(MockMvcRequestBuilders.post("/internal/coupon/use")
                        .param("user_id", "100").param("user_coupon_id", "55").param("order_no", "ORD-1")
                        .param("order_amount", "10000").param("order_type", "1").param("seller_id", "9")
                        .param("product_ids", "1001,1002").param("category_ids", "7,8"))
                .andExpect(status().isOk());

        verify(couponUsageManager).useCoupon(eq(100L), eq(55L), eq("ORD-1"), eq(10000), eq(1), eq(9L),
                argThat(ids -> "1001,1002".equals(ids)), argThat(ids -> "7,8".equals(ids)));
    }

    @Test
    @DisplayName("use — camelCase 查询参数名不再绑定 → 400（线上名已切 snake，反例守护）")
    void shouldReturnBadRequest_whenCamelCaseQueryNamesUsed() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/internal/coupon/use")
                        .param("userId", "100").param("userCouponId", "55").param("orderNo", "ORD-1")
                        .param("orderAmount", "10000").param("orderType", "1").param("sellerId", "9"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1101));

        verifyNoInteractions(couponUsageManager);
    }

    private static CouponStackInfoCO stackInfo(Long id, String stackGroup) {
        CouponStackInfoCO info = new CouponStackInfoCO();
        info.setId(id);
        info.setCouponId("0f1e2d3c4b5a69788796a5b4c3d2e1f0");
        info.setStackRule(2);
        info.setStackGroup(stackGroup);
        return info;
    }
}
