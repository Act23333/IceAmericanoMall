package org.icedamericanomall.trade.adapter.admin;

import org.icedamericanomall.trade.app.aftersale.AfterSaleManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 售后审核 / 确认收货 的适配层接线测试（F1；触发时机 V5.6 纠正，ADR-037）。
 *
 * <p>控制器只做参数接收与委托——「仅退款审核同意触发 / 退货退款确认收货触发 /
 * 触发发生在落库之后」三条退款触发不变量的断言在 {@code AfterSaleManagerTest}（应用层）。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AdminAfterSaleController — 委托 AfterSaleManager（审核 / 确认收货）")
class AdminAfterSaleControllerTest {

    @Mock AfterSaleManager afterSaleManager;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AdminAfterSaleController(afterSaleManager))
                // 批次 ①：售后单不存在的 404 断言需要全局处理器在场（standalone 不自动装配）
                .setControllerAdvice(new org.icedamericanomall.common.aspect.GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("审核 — 委托 afterSaleManager.review(id, status, remark)")
    void shouldDelegateReview() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.put("/api/admin/after-sale/9/review")
                        .param("status", "2").param("remark", "同意"))
                .andExpect(status().isOk());

        verify(afterSaleManager).review(9L, 2, "同意");
    }

    @Test
    @DisplayName("确认收到退货 — 委托 afterSaleManager.confirmReturn(id)")
    void shouldDelegateConfirmReturn() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.put("/api/admin/after-sale/9/confirm-return"))
                .andExpect(status().isOk());

        verify(afterSaleManager).confirmReturn(9L);
    }

    /**
     * 批次 ① 状态码归位：{@code APPLICATION_NOT_FOUND(3032)} 由 200 改为 <b>404</b>。
     *
     * <p><b>改前必失败</b>：审核一个不存在的售后单，改造前返回 <b>HTTP 200 + body code 3032</b>；
     * 现返回 404，业务码仍 3032（≠1103）。
     *
     * <p>同族的 {@code confirmReturn} 走 {@code NO_RESOURCE_FOUND}(1103)，两者在 HTTP 层
     * 现同为 404 —— 该收敛由 {@code AfterSaleManager} 的类注释登记，本批未合并业务码。
     */
    @Test
    @DisplayName("审核 — 售后单不存在 → 404 + 3032（不再是 200）")
    void shouldReturn404_whenAfterSaleMissing() throws Exception {
        org.mockito.Mockito.doThrow(new org.icedamericanomall.common.exception.BizException(
                        org.icedamericanomall.common.enums.ErrorCode.APPLICATION_NOT_FOUND, "售后申请不存在"))
                .when(afterSaleManager).review(9L, 2, "同意");

        mockMvc.perform(MockMvcRequestBuilders.put("/api/admin/after-sale/9/review")
                        .param("status", "2").param("remark", "同意"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(3032))
                .andExpect(jsonPath("$.msg").value("售后申请不存在"));
    }
}
