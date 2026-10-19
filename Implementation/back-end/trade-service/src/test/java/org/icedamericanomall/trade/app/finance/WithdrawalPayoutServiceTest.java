package org.icedamericanomall.trade.app.finance;

import org.icedamericanomall.trade.domain.model.finance.Withdrawal;
import org.icedamericanomall.trade.domain.port.WithdrawalPayoutPort;
import org.icedamericanomall.trade.domain.repository.WithdrawalRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 出款执行器单测：**「结果未知」与「失败」必须分开**。
 *
 * <p>这里只验证代码路径；真实失败分支要等渠道接入后的第一次失败回执（§1.36 已登记为未验证）。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("WithdrawalPayoutService 出款执行器单元测试")
class WithdrawalPayoutServiceTest {

    private static final Long WITHDRAWAL_ID = 77L;
    private static final Long SELLER_ID = 9L;
    private static final String WITHDRAWAL_NO = "WD-1";
    private static final int LIMIT = 100;

    @Mock private WithdrawalRepository withdrawalRepository;
    @Mock private WithdrawalPayoutPort payoutPort;
    @Mock private WithdrawalManager withdrawalManager;
    @Mock private Withdrawal withdrawal;

    @InjectMocks private WithdrawalPayoutService payoutService;

    @Test
    @DisplayName("渠道未受理 ⇒ 保持「付款中」：不落账、不释放冻结（结果未知 ≠ 失败）")
    void shouldKeepPaying_whenChannelNotAccepted() {
        when(withdrawalRepository.findPayingIds(LIMIT)).thenReturn(List.of(WITHDRAWAL_ID));
        stubWithdrawal();
        when(payoutPort.pay(any()))
                .thenReturn(new WithdrawalPayoutPort.PayoutResult(false, null, "CHANNEL_DOWN"));

        assertEquals(0, payoutService.settle(LIMIT), "未受理不得计入完成");
        verify(withdrawalManager, never()).completePayout(any());
    }

    @Test
    @DisplayName("渠道受理 ⇒ 落账一次；幂等键复用 withdrawalNo（重试不会出两次钱）")
    void shouldSettle_whenChannelAccepted() {
        when(withdrawalRepository.findPayingIds(LIMIT)).thenReturn(List.of(WITHDRAWAL_ID));
        stubWithdrawal();
        ArgumentCaptor<WithdrawalPayoutPort.PayoutCommand> captor =
                ArgumentCaptor.forClass(WithdrawalPayoutPort.PayoutCommand.class);
        when(payoutPort.pay(captor.capture()))
                .thenReturn(new WithdrawalPayoutPort.PayoutResult(true, "MOCK-REF", null));
        when(withdrawalManager.completePayout(withdrawal)).thenReturn(true);

        assertEquals(1, payoutService.settle(LIMIT));
        verify(withdrawalManager).completePayout(withdrawal);
        assertEquals(WITHDRAWAL_NO, captor.getValue().payoutNo(),
                "幂等键必须是单据业务号：重试复用同一个，渠道才能去重");
    }

    private void stubWithdrawal() {
        when(withdrawalRepository.findById(WITHDRAWAL_ID)).thenReturn(withdrawal);
        when(withdrawal.getId()).thenReturn(WITHDRAWAL_ID);
        when(withdrawal.getSellerId()).thenReturn(SELLER_ID);
        when(withdrawal.getWithdrawalNo()).thenReturn(WITHDRAWAL_NO);
        when(withdrawal.getAmount()).thenReturn(4_000);
    }
}
