package org.icedamericanomall.trade.app.finance;

import org.icedamericanomall.trade.domain.model.finance.Withdrawal;
import org.icedamericanomall.trade.domain.port.WithdrawalPayoutPort;
import org.icedamericanomall.trade.domain.repository.WithdrawalRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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

    private WithdrawalPayoutService payoutService;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        payoutService = new WithdrawalPayoutService(withdrawalRepository, payoutPort, withdrawalManager,
                java.time.Clock.fixed(java.time.Instant.parse("2026-10-19T00:00:00Z"), java.time.ZoneId.of("UTC")));
        org.springframework.test.util.ReflectionTestUtils.setField(payoutService, "retryIntervalMinutes", 30);
        // lenient：截断用例不经过扫描/标记路径，严格桩会误报 UnnecessaryStubbing
        org.mockito.Mockito.lenient().when(withdrawalRepository.markPayoutAttempt(any(), any())).thenReturn(1);
    }

    @Test
    @DisplayName("渠道未受理 ⇒ 保持「付款中」：不落账、不释放冻结（结果未知 ≠ 失败）")
    void shouldKeepPaying_whenChannelNotAccepted() {
        when(withdrawalRepository.findDuePayingIds(any(), anyInt())).thenReturn(List.of(WITHDRAWAL_ID));
        stubWithdrawal();
        when(payoutPort.pay(any()))
                .thenReturn(new WithdrawalPayoutPort.PayoutResult(false, null, "CHANNEL_DOWN"));

        assertEquals(0, payoutService.settle(LIMIT), "未受理不得计入完成");
        verify(withdrawalManager, never()).completePayout(any());
    }

    @Test
    @DisplayName("渠道受理 ⇒ 落账一次；幂等键复用 withdrawalNo（重试不会出两次钱）")
    void shouldSettle_whenChannelAccepted() {
        when(withdrawalRepository.findDuePayingIds(any(), anyInt())).thenReturn(List.of(WITHDRAWAL_ID));
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

    @Test
    @DisplayName("service — 同一单 settle 两次：渠道被调两次，但**只记账一次**（收口在 completePayout 的返回值）")
    void shouldAccountOnce_whenSettleRunsTwice() {
        when(withdrawalRepository.findDuePayingIds(any(), anyInt())).thenReturn(List.of(WITHDRAWAL_ID));
        stubWithdrawal();
        when(payoutPort.pay(any()))
                .thenReturn(new WithdrawalPayoutPort.PayoutResult(true, "MOCK-WD-1", null));
        // 第一次落账成功；第二次状态已变（幂等）⇒ 返回 false
        when(withdrawalManager.completePayout(withdrawal)).thenReturn(true, false);

        assertEquals(1, payoutService.settle(LIMIT));
        assertEquals(0, payoutService.settle(LIMIT), "第二次不得再计完成");

        verify(payoutPort, times(2)).pay(any());          // ★ job 层确实调了两次
        verify(withdrawalManager, times(2)).completePayout(withdrawal);
        // 记账只发生一次：第二次 completePayout 返回 false，服务不往下推
    }

    @Test
    @DisplayName("节流标记 0 行（状态已变）⇒ 根本不调渠道（先标记后调用的意义）")
    void shouldSkipChannel_whenAttemptMarkMisses() {
        when(withdrawalRepository.findDuePayingIds(any(), anyInt())).thenReturn(List.of(WITHDRAWAL_ID));
        when(withdrawalRepository.markPayoutAttempt(any(), any())).thenReturn(0);
        // 注意：这里**不能**桩 findById —— 标记 0 行就该直接跳过，连单子都不读

        assertEquals(0, payoutService.settle(LIMIT));
        verify(payoutPort, never()).pay(any());
    }

    @Test
    @DisplayName("结果摘要超长 ⇒ 截断到列宽内且**保留尾部**（渠道号唯一段在末尾）")
    void summarizeShouldTruncateKeepingTail() {
        String longRef = "X".repeat(200);
        String stored = WithdrawalPayoutService.summarize(
                new WithdrawalPayoutPort.PayoutResult(true, longRef, null));

        assertTrue(stored.length() <= WithdrawalPayoutService.MAX_RESULT_LEN,
                "必须落在列宽内，否则写库会被截断（静默）或报错");
        assertTrue(stored.startsWith("ACCEPTED:..."), "保留分类前缀 + 截断标记");
        assertTrue(stored.endsWith(longRef.substring(longRef.length() - 20)), "尾部必须是原文尾部，不能截头");
    }

    private void stubWithdrawal() {
        when(withdrawalRepository.findById(WITHDRAWAL_ID)).thenReturn(withdrawal);
        when(withdrawal.getId()).thenReturn(WITHDRAWAL_ID);
        when(withdrawal.getSellerId()).thenReturn(SELLER_ID);
        when(withdrawal.getWithdrawalNo()).thenReturn(WITHDRAWAL_NO);
        when(withdrawal.getAmount()).thenReturn(4_000);
    }
}
