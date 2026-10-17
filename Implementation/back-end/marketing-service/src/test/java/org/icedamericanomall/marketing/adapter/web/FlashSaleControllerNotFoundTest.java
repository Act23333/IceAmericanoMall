package org.icedamericanomall.marketing.adapter.web;

import org.icedamericanomall.common.aspect.GlobalExceptionHandler;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.marketing.app.MarketingManager;
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
 * 秒杀抢购（{@code POST /api/flash/buy}）的存在性承载 —— 批次 ① 状态码归位。
 *
 * <p><b>改前必失败</b>：改造前 {@code FLASH_SALE_NOT_FOUND(3020)} 配 200，
 * 抢一个<b>已被删除的活动</b>返回 <b>HTTP 200 + body code 3020</b>；现返回 <b>404</b>。
 *
 * <p>同时守住同族的<b>业务规则拒绝</b>：{@code FLASH_SALE_NOT_STARTED}(3021)、
 * {@code FLASH_SALE_SOLD_OUT}(3022)、{@code FLASH_SALE_LIMIT_EXCEEDED}(3023)
 * —— 场次存在、规则不允许，<b>必须仍是 200</b>，不得被本批顺手改成 4xx。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("FlashSaleController — 活动不存在 404/3020；未开始/售罄/超限仍 200")
class FlashSaleControllerNotFoundTest {

    @Mock
    private MarketingManager marketingManager;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new FlashSaleController(marketingManager))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("活动不存在 → 404 + 3020（不再是 200）")
    void shouldReturn404_whenFlashSaleMissing() throws Exception {
        when(marketingManager.buyFlashSale(9L, 1L))
                .thenThrow(new BizException(ErrorCode.FLASH_SALE_NOT_FOUND));

        mockMvc.perform(MockMvcRequestBuilders.post("/api/flash/buy")
                        .param("flash_id", "9").param("address_id", "1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(3020))
                .andExpect(jsonPath("$.msg").value("秒杀活动不存在"));
    }

    @Test
    @DisplayName("售罄 → 仍 200 + 3022（业务规则拒绝，未被本批波及）")
    void shouldKeep200_whenSoldOut() throws Exception {
        when(marketingManager.buyFlashSale(9L, 1L))
                .thenThrow(new BizException(ErrorCode.FLASH_SALE_SOLD_OUT));

        mockMvc.perform(MockMvcRequestBuilders.post("/api/flash/buy")
                        .param("flash_id", "9").param("address_id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(3022));
    }

    @Test
    @DisplayName("未开始/已结束 → 仍 200 + 3021（业务规则拒绝，未被本批波及）")
    void shouldKeep200_whenNotStarted() throws Exception {
        when(marketingManager.buyFlashSale(9L, 1L))
                .thenThrow(new BizException(ErrorCode.FLASH_SALE_NOT_STARTED));

        mockMvc.perform(MockMvcRequestBuilders.post("/api/flash/buy")
                        .param("flash_id", "9").param("address_id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(3021));
    }
}
