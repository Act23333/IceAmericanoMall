package org.icedamericanomall.merchant.adapter.web;

import org.icedamericanomall.common.aspect.GlobalExceptionHandler;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.merchant.app.seller.ShopManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 公开店铺页（{@code GET /api/shops/{shopId}}）的<b>存在性承载</b>——批次 ① 状态码归位。
 *
 * <p><b>本用例改前必失败</b>：改造前 {@code SHOP_NOT_FOUND(3015)} 配 {@code HttpStatus.OK}，
 * 本端点对「不存在的店铺」返回 <b>HTTP 200 + body code 3015</b>；现返回 <b>404</b>。
 * 断言里同时钉住「业务码仍是 3015（不是 1103）」—— 前端靠这个码区分「店铺不存在」与
 * 「订单不存在 / SKU 不存在」，合并成单一 1103 会丢信息。
 *
 * <p><b>同时守住批次 ③ 的成果</b>：第二、三条用例断言「状态不允许（3000）」与
 * 「归属他人（403）」<b>没有</b>被本批顺手改成 404 —— 那等于把 ③ 拆出去的语义又合流回来。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ShopsController — 店铺不存在 404/3015；状态不允许仍 200/3000")
class ShopsControllerNotFoundTest {

    @Mock
    private ShopManager shopManager;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ShopsController(shopManager))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("店铺不存在 → HTTP 404 + Result(3015)（不再是 200）")
    void shouldReturn404_whenShopMissing() throws Exception {
        when(shopManager.getVisibleShop(999L)).thenThrow(new BizException(ErrorCode.SHOP_NOT_FOUND));

        mockMvc.perform(MockMvcRequestBuilders.get("/api/shops/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(3015))
                .andExpect(jsonPath("$.msg").value("店铺不存在"));
    }

    @Test
    @DisplayName("店铺存在但状态不允许（审核中/冻结）→ 仍 200 + 3000（批次 ③ 成果不得被推翻）")
    void shouldKeep200_whenShopStatusNotAllowed() throws Exception {
        when(shopManager.getVisibleShop(1L)).thenThrow(new BizException(
                ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "店铺当前不可访问（审核中或已冻结）"));

        mockMvc.perform(MockMvcRequestBuilders.get("/api/shops/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(3000))
                .andExpect(jsonPath("$.msg").value("店铺当前不可访问（审核中或已冻结）"));
    }

    @Test
    @DisplayName("店铺归属他人 → 仍 403 + 1013（不得被 not-found 掩盖）")
    void shouldKeep403_whenShopOwnedByAnother() throws Exception {
        when(shopManager.getVisibleShop(2L)).thenThrow(
                new org.icedamericanomall.common.exception.ForbiddenException(
                        ErrorCode.FORBIDDEN, "无权操作该店铺"));

        mockMvc.perform(MockMvcRequestBuilders.get("/api/shops/2"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(1013));
    }
}
