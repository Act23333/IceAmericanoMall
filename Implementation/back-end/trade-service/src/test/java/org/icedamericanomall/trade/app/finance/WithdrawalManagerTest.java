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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * WithdrawalManager 单元测试 —— 提现金额校验（>0 且 ≤ 可用余额）与审核状态合法性（含级联打款）。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("WithdrawalManager 提现校验单元测试")
class WithdrawalManagerTest {

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

    @Test
    @DisplayName("applyWithdrawal — 金额为 0/负数：抛 PARAM_ERROR 且不落库")
    void shouldReject_whenAmountNotPositive() {
        BizException ex = assertThrows(BizException.class,
                () -> manager.applyWithdrawal(9L, req(0)));
        assertEquals(ErrorCode.PARAM_ERROR, ex.getErrorCode());

        assertThrows(BizException.class, () -> manager.applyWithdrawal(9L, req(-100)));
        verify(withdrawalRepository, never()).save(any(Withdrawal.class));
    }

    @Test
    @DisplayName("applyWithdrawal — 金额超过可用余额：抛 BALANCE_INSUFFICIENT 且不落库")
    void shouldReject_whenAmountExceedsAvailableBalance() {
        when(settlementManager.availableBalance(9L)).thenReturn(5000);

        BizException ex = assertThrows(BizException.class,
                () -> manager.applyWithdrawal(9L, req(5001)));
        assertEquals(ErrorCode.BALANCE_INSUFFICIENT, ex.getErrorCode());
        verify(withdrawalRepository, never()).save(any(Withdrawal.class));
    }

    @Test
    @DisplayName("applyWithdrawal — 金额合法：落库待审核提现单")
    void shouldSavePendingWithdrawal_whenAmountValid() {
        when(settlementManager.availableBalance(9L)).thenReturn(5000);
        when(withdrawalRepository.save(any(Withdrawal.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        manager.applyWithdrawal(9L, req(5000));

        ArgumentCaptor<Withdrawal> captor = ArgumentCaptor.forClass(Withdrawal.class);
        verify(withdrawalRepository).save(captor.capture());
        Withdrawal saved = captor.getValue();
        assertEquals(WithdrawalStatusEnum.PENDING_REVIEW.getCode(), saved.getStatus());
        assertEquals(5000, saved.getAmount());
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
    }

    @Test
    @DisplayName("review — 打款成功：级联更新结算单为已打款")
    void shouldCascadeSettlementPaidOut_whenReviewPaidOut() {
        Withdrawal w = new Withdrawal();
        w.setId(1L);
        w.setSellerId(9L);
        w.setStatus(WithdrawalStatusEnum.PENDING_REVIEW.getCode());
        when(withdrawalRepository.findById(1L)).thenReturn(w);

        manager.review(1L, WithdrawalStatusEnum.PAID_OUT.getCode(), "已打款");

        assertEquals(WithdrawalStatusEnum.PAID_OUT.getCode(), w.getStatus());
        verify(withdrawalRepository).update(w);
        verify(settlementManager).markSellerSettlementsPaidOut(9L);
    }

    @Test
    @DisplayName("review — 审核拒绝：不级联更新结算单")
    void shouldNotCascade_whenReviewRejected() {
        Withdrawal w = new Withdrawal();
        w.setId(1L);
        w.setSellerId(9L);
        when(withdrawalRepository.findById(1L)).thenReturn(w);

        manager.review(1L, WithdrawalStatusEnum.REJECTED.getCode(), "信息不全");

        verify(settlementManager, never()).markSellerSettlementsPaidOut(anyLong());
    }
}
