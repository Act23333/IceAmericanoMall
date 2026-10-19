package org.icedamericanomall.trade.app.finance;

import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.trade.adapter.assembler.TradeConverterImpl;
import org.icedamericanomall.trade.adapter.request.WithdrawalApplyReq;
import org.icedamericanomall.trade.domain.enums.WithdrawalStatusEnum;
import org.icedamericanomall.trade.domain.model.finance.Withdrawal;
import org.icedamericanomall.trade.domain.repository.WithdrawalRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * WithdrawalManager 单元测试 —— 提现金额校验（>0 且 ≤ 可用余额）与审核状态合法性。
 *
 * <p><b>打款面的口径已改（T-WITHDRAW 第一步）</b>：审核为「已打款」不再按商家<b>全量</b>把结算单置为
 * 已打款（提现 100 会把 1000 全置成已打款，剩 900 看似再也提不出来），改为在权威资金账户上记账
 * {@code paid_total += amount}（{@link SettlementManager#payout}）。
 *
 * <p><b>两处「不许顺手加回来」</b>：
 * <ol>
 *   <li>审核侧<b>不判额度</b>：额度属申请侧（{@code applyWithdrawal}，第二步做成条件预占）；
 *       审核再判一次会制造「申请通过、打款被拒，而钱已付出」的假拒绝。</li>
 *   <li>审核<b>不做读-改-写</b>：状态迁移是条件更新（待审核 → 终态，0 行即幂等），
 *       重复审核不得再记一次账。</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("WithdrawalManager 提现校验单元测试")
class WithdrawalManagerTest {

    private static final long SELLER_ID = 9L;
    private static final int AMOUNT = 5000;

    @Mock private SettlementManager settlementManager;
    @Mock private WithdrawalRepository withdrawalRepository;

    private WithdrawalManager manager;

    @BeforeEach
    void setUp() {
        manager = new WithdrawalManager(withdrawalRepository, settlementManager, new TradeConverterImpl());
    }

    private WithdrawalApplyReq req(Integer amount) {
        WithdrawalApplyReq r = new WithdrawalApplyReq();
        r.setAmount(amount);
        r.setBankAccount("6222****1234");
        r.setBankName("招商银行");
        return r;
    }

    private Withdrawal pendingWithdrawal() {
        Withdrawal w = new Withdrawal();
        w.setId(1L);
        w.setSellerId(SELLER_ID);
        w.setAmount(AMOUNT);
        w.setStatus(WithdrawalStatusEnum.PENDING_REVIEW.getCode());
        return w;
    }

    @Test
    @DisplayName("applyWithdrawal — 金额为 0/负数：抛 PARAM_ERROR 且不落库")
    void shouldReject_whenAmountNotPositive() {
        BizException ex = assertThrows(BizException.class,
                () -> manager.applyWithdrawal(SELLER_ID, req(0)));
        assertEquals(ErrorCode.PARAM_ERROR, ex.getErrorCode());

        assertThrows(BizException.class, () -> manager.applyWithdrawal(SELLER_ID, req(-100)));
        verify(withdrawalRepository, never()).save(any(Withdrawal.class));
    }

    @Test
    @DisplayName("applyWithdrawal — 金额超过可用余额：抛 BALANCE_INSUFFICIENT 且不落库")
    void shouldReject_whenAmountExceedsAvailableBalance() {
        when(settlementManager.availableBalance(SELLER_ID)).thenReturn(5000);

        BizException ex = assertThrows(BizException.class,
                () -> manager.applyWithdrawal(SELLER_ID, req(5001)));
        assertEquals(ErrorCode.BALANCE_INSUFFICIENT, ex.getErrorCode());
        verify(withdrawalRepository, never()).save(any(Withdrawal.class));
    }

    @Test
    @DisplayName("applyWithdrawal — 金额合法：落库待审核提现单")
    void shouldSavePendingWithdrawal_whenAmountValid() {
        when(settlementManager.availableBalance(SELLER_ID)).thenReturn(5000);
        when(withdrawalRepository.save(any(Withdrawal.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        manager.applyWithdrawal(SELLER_ID, req(AMOUNT));

        ArgumentCaptor<Withdrawal> captor = ArgumentCaptor.forClass(Withdrawal.class);
        verify(withdrawalRepository).save(captor.capture());
        Withdrawal saved = captor.getValue();
        assertEquals(WithdrawalStatusEnum.PENDING_REVIEW.getCode(), saved.getStatus());
        assertEquals(AMOUNT, saved.getAmount());
        assertNotNull(saved.getWithdrawalNo());
    }

    @Test
    @DisplayName("review — 非法状态值：抛 PARAM_ERROR")
    void shouldRejectReview_whenStatusIllegal() {
        BizException ex = assertThrows(BizException.class, () -> manager.review(1L, 99, "备注"));
        assertEquals(ErrorCode.PARAM_ERROR, ex.getErrorCode());
        verify(withdrawalRepository, never()).findById(anyLong());
    }

    @Test
    @DisplayName("review — 提现单不存在：抛 WITHDRAWAL_NOT_FOUND")
    void shouldRejectReview_whenEntityMissing() {
        when(withdrawalRepository.findById(1L)).thenReturn(null);

        BizException ex = assertThrows(BizException.class,
                () -> manager.review(1L, WithdrawalStatusEnum.PAID_OUT.getCode(), "已打款"));
        assertEquals(ErrorCode.WITHDRAWAL_NOT_FOUND, ex.getErrorCode());
        verify(settlementManager, never()).payout(anyLong(), anyInt());
    }

    @Test
    @DisplayName("review — 打款成功：条件迁移命中后在资金账户上记账（paid_total += amount）")
    void shouldRecordPayoutOnFundAccount_whenReviewPaidOut() {
        Withdrawal w = pendingWithdrawal();
        when(withdrawalRepository.findById(1L)).thenReturn(w);
        when(withdrawalRepository.reviewIfPending(1L,
                WithdrawalStatusEnum.PENDING_REVIEW.getCode(), WithdrawalStatusEnum.PAID_OUT.getCode(),
                "已打款")).thenReturn(1);

        manager.review(1L, WithdrawalStatusEnum.PAID_OUT.getCode(), "已打款");

        verify(settlementManager).payout(SELLER_ID, AMOUNT);
    }

    @Test
    @DisplayName("review — 重复审核（条件迁移 0 行）：幂等返回，**不再记账**（否则 paid_total 记两遍）")
    void shouldNotRecordPayoutAgain_whenReviewReplayed() {
        Withdrawal w = pendingWithdrawal();
        when(withdrawalRepository.findById(1L)).thenReturn(w);
        when(withdrawalRepository.reviewIfPending(anyLong(), anyInt(), anyInt(), any())).thenReturn(0);

        manager.review(1L, WithdrawalStatusEnum.PAID_OUT.getCode(), "已打款");

        verify(settlementManager, never()).payout(anyLong(), anyInt());
    }

    @Test
    @DisplayName("review — 审核侧不判额度：记账不被任何余额条件拦下（额度属申请侧）")
    void shouldNotCheckAvailableBalance_whenReviewPaidOut() {
        Withdrawal w = pendingWithdrawal();
        when(withdrawalRepository.findById(1L)).thenReturn(w);
        when(withdrawalRepository.reviewIfPending(anyLong(), anyInt(), anyInt(), any())).thenReturn(1);

        manager.review(1L, WithdrawalStatusEnum.PAID_OUT.getCode(), "已打款");

        // 本批最容易顺手加错的一条：review 里若再问一次「余额够不够」，就会出现
        // 「申请时通过、打款时被拒，而钱已经付出去了」的假拒绝。
        verify(settlementManager, never()).availableBalance(anyLong());
        verify(settlementManager).payout(SELLER_ID, AMOUNT);
    }

    @Test
    @DisplayName("review — 审核拒绝：条件迁移命中但不在资金账户上记账")
    void shouldNotPayout_whenReviewRejected() {
        Withdrawal w = pendingWithdrawal();
        when(withdrawalRepository.findById(1L)).thenReturn(w);
        when(withdrawalRepository.reviewIfPending(1L,
                WithdrawalStatusEnum.PENDING_REVIEW.getCode(), WithdrawalStatusEnum.REJECTED.getCode(),
                "信息不全")).thenReturn(1);

        manager.review(1L, WithdrawalStatusEnum.REJECTED.getCode(), "信息不全");

        verify(settlementManager, never()).payout(anyLong(), anyInt());
    }
}
