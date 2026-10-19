package org.icedamericanomall.trade.adapter.admin;

import org.icedamericanomall.common.aspect.GlobalExceptionHandler;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.trade.app.finance.SettlementManager;
import org.icedamericanomall.trade.app.finance.SettlementSweeper;
import org.icedamericanomall.trade.app.finance.WithdrawalManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 提现审核（{@code PUT /api/admin/finance/withdrawal/{id}/review}）的存在性承载 —— 批次 ①。
 *
 * <p><b>改前必失败</b>：改造前 {@code WITHDRAWAL_NOT_FOUND(3033)} 配 200，
 * 审核一个不存在的提现单返回 <b>HTTP 200 + body code 3033</b>；现返回 <b>404</b>。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AdminFinanceController — 提现单不存在 404/3033")
class AdminFinanceControllerNotFoundTest {

    @Mock
    private SettlementManager settlementManager;

    @Mock
    private SettlementSweeper settlementSweeper;

    @Mock
    private WithdrawalManager withdrawalManager;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AdminFinanceController(settlementManager, settlementSweeper, withdrawalManager))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("提现单不存在 → 404 + 3033（不再是 200）")
    void shouldReturn404_whenWithdrawalMissing() throws Exception {
        doThrow(new BizException(ErrorCode.WITHDRAWAL_NOT_FOUND, "提现申请不存在"))
                .when(withdrawalManager).review(9L, 2, "同意");

        mockMvc.perform(MockMvcRequestBuilders
                        .put("/api/admin/finance/withdrawal/9/review")
                        .param("status", "2").param("remark", "同意"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(3033))
                .andExpect(jsonPath("$.msg").value("提现申请不存在"));
    }

    @Test
    @DisplayName("非法状态值 → 仍 400 + 1101（参数错误，未被本批波及）")
    void shouldKeep400_whenStatusIllegal() throws Exception {
        doThrow(new BizException(ErrorCode.PARAM_ERROR, "非法提现状态: 99"))
                .when(withdrawalManager).review(9L, 99, null);

        mockMvc.perform(MockMvcRequestBuilders
                        .put("/api/admin/finance/withdrawal/9/review")
                        .param("status", "99"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1101));
    }
}
