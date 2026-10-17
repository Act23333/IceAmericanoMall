package org.icedamericanomall.trade.adapter.web;

import org.icedamericanomall.common.security.UserContext;
import org.icedamericanomall.common.security.UserInfo;
import org.icedamericanomall.trade.adapter.response.SellerBalanceVO;
import org.icedamericanomall.trade.app.finance.SettlementManager;
import org.icedamericanomall.trade.app.finance.WithdrawalManager;
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
 * SellerFinanceController 余额端点接线测试（批次 D）。
 *
 * <p>改造前：Controller 内 {@code Map.of("sellerId", sellerId, "balance", availableBalance)}
 * 就地拼弱类型报文。改造后：装配下沉到
 * {@link SettlementManager#sellerBalance(Long)}，Controller 只透出
 * {@link SellerBalanceVO}。本测试钉住「身份仍取 {@code UserContext}」+「余额为分、原样透出」。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SellerFinanceController — T22 余额：Manager 装配 VO")
class SellerFinanceControllerTest {

    private static final Long SELLER_ID = 9L;

    @Mock
    private SettlementManager settlementManager;

    @Mock
    private WithdrawalManager withdrawalManager;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new SellerFinanceController(settlementManager, withdrawalManager))
                .build();
        UserContext.setUser(UserInfo.builder().userId(SELLER_ID).build());
    }

    @AfterEach
    void tearDown() {
        UserContext.removeUser();
    }

    @Test
    @DisplayName("balance — 委托 SettlementManager#sellerBalance(当前商家)，余额为分原样透出")
    void shouldDelegateSellerBalance_whenBalance() throws Exception {
        SellerBalanceVO vo = new SellerBalanceVO();
        vo.setSellerId(SELLER_ID);
        vo.setBalance(1280000);
        when(settlementManager.sellerBalance(SELLER_ID)).thenReturn(vo);

        mockMvc.perform(MockMvcRequestBuilders.get("/api/seller/finance/balance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sellerId").value(9))
                .andExpect(jsonPath("$.data.balance").value(1280000));

        verify(settlementManager).sellerBalance(eq(SELLER_ID));
    }
}
