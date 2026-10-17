package org.icedamericanomall.marketing.adapter.web;

import org.icedamericanomall.common.security.UserContext;
import org.icedamericanomall.common.security.UserInfo;
import org.icedamericanomall.marketing.adapter.response.UserCouponCountVO;
import org.icedamericanomall.marketing.app.coupon.CouponAdminManager;
import org.icedamericanomall.marketing.app.coupon.CouponClaimManager;
import org.icedamericanomall.marketing.app.coupon.CouponUsageManager;
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

import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CouponController 适配层接线测试（V5.11 M7 我的券三态计数）。
 *
 * <p>控制器只做身份解析与委托：{@code GET /api/coupon/count} 取 {@code UserContext}
 * 当前用户（网关注入，禁止信任前端传参）委托 {@code CouponUsageManager.countMyCoupons}，
 * {@code Result} 包装透出。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CouponController — M7 委托 / Result 包装")
class CouponControllerTest {

    private static final Long USER_ID = 9L;

    @Mock
    private CouponClaimManager couponClaimManager;

    @Mock
    private CouponUsageManager couponUsageManager;

    @Mock
    private CouponAdminManager couponAdminManager;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new CouponController(couponClaimManager, couponUsageManager, couponAdminManager))
                // 批次 ①：领券「券不存在」要断言 404，需要全局处理器在场（standalone 不自动装配）
                .setControllerAdvice(new org.icedamericanomall.common.aspect.GlobalExceptionHandler())
                .build();
        UserContext.setUser(UserInfo.builder().userId(USER_ID).build());
    }

    @AfterEach
    void tearDown() {
        UserContext.removeUser();
    }

    @Test
    @DisplayName("count — 当前用户（UserContext）委托 CouponUsageManager，Result 包装三态计数")
    void shouldDelegateWithCurrentUser_whenCountCoupons() throws Exception {
        when(couponUsageManager.countMyCoupons(USER_ID))
                .thenReturn(UserCouponCountVO.of(2, 1, 3));

        mockMvc.perform(MockMvcRequestBuilders.get("/api/coupon/count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.available").value(2))
                .andExpect(jsonPath("$.data.used").value(1))
                .andExpect(jsonPath("$.data.expired").value(3));

        verify(couponUsageManager).countMyCoupons(eq(USER_ID));
    }

    /**
     * 批次 ① 状态码归位：{@code COUPON_NOT_FOUND(3019)} 由 200 改为 <b>404</b>。
     *
     * <p><b>改前必失败</b>：抢一个不存在的券，改造前返回 <b>HTTP 200 + body code 3019</b>
     * （前端只有解 body 才知道失败）；现返回 404，业务码仍 3019（≠1103「静态资源」）。
     */
    @Test
    @DisplayName("grab — 券不存在 → 404 + 3019（不再是 200）")
    void shouldReturn404_whenCouponMissingOnGrab() throws Exception {
        when(couponClaimManager.claimWithGrab(eq(USER_ID), eq("C-NOT-EXIST")))
                .thenThrow(new org.icedamericanomall.common.exception.BizException(
                        org.icedamericanomall.common.enums.ErrorCode.COUPON_NOT_FOUND));

        mockMvc.perform(MockMvcRequestBuilders.post("/api/coupon/grab")
                        .param("coupon_id", "C-NOT-EXIST"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(3019))
                .andExpect(jsonPath("$.msg").value("优惠券不存在"));
    }
}
