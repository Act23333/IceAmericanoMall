package org.icedamericanomall.trade.app.finance;

import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.trade.adapter.assembler.TradeConverterImpl;
import org.icedamericanomall.trade.adapter.request.WithdrawalApplyReq;
import org.icedamericanomall.trade.domain.enums.WithdrawalStatusEnum;
import org.icedamericanomall.trade.domain.model.finance.SettlementRemaining;
import org.icedamericanomall.trade.domain.model.finance.Withdrawal;
import org.icedamericanomall.trade.domain.repository.SellerFundAccountRepository;
import org.icedamericanomall.trade.domain.repository.SettlementRepository;
import org.icedamericanomall.trade.domain.repository.WithdrawalRepository;
import org.icedamericanomall.trade.domain.repository.WithdrawalSettlementRepository;
import org.icedamericanomall.trade.domain.service.SettlementAllocator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@code WithdrawalManager} 单测 —— 第二步（预占）与第三步（核销）之后，它要守四条顺序/分支：
 *
 * <ol>
 *   <li>申请：<b>冻结 → 落单 → 核销</b>，且核销必须<b>足额</b>（规划合计 ≠ 提现金额 ⇒ 失败关闭）；</li>
 *   <li>拒绝：<b>先迁移状态</b>（0 行 = 重复审核 ⇒ 幂等返回、不碰账户）、再<b>退核销</b>、再解冻；</li>
 *   <li>打款：迁移 → 解冻 → 记账，<b>不</b>退核销（钱确实从那些单出）；</li>
 *   <li>审核侧<b>不判额度</b>（额度属申请侧的条件预占）。</li>
 * </ol>
 * 真实 SQL 行为（额度不足 / 核销额不足 / 冻结额不足）由 {@code SettlementFundAccountH2Test} 用真库证；
 * 这里只验编排顺序与分支。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("WithdrawalManager 提现编排单元测试")
class WithdrawalManagerTest {

    private static final long SELLER_ID = 9L;
    private static final int AMOUNT = 5000;
    private static final long WITHDRAWAL_ID = 7L;
    private static final long SETTLEMENT_ID = 11L;
    private static final int MAX_PER_REQUEST = 5_000_000;

    @Mock private SettlementManager settlementManager;
    @Mock private WithdrawalRepository withdrawalRepository;
    @Mock private SellerFundAccountRepository fundAccountRepository;
    @Mock private SettlementRepository settlementRepository;
    @Mock private WithdrawalSettlementRepository withdrawalSettlementRepository;

    private WithdrawalManager manager;

    @BeforeEach
    void setUp() {
        manager = new WithdrawalManager(withdrawalRepository, fundAccountRepository,
                settlementRepository, withdrawalSettlementRepository, settlementManager,
                new TradeConverterImpl());
        // 生产默认关闭（只记账不付款）；本单测要验「打款路径」，显式打开
        org.springframework.test.util.ReflectionTestUtils.setField(manager, "payoutEnabled", true);
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
        w.setId(WITHDRAWAL_ID);
        w.setSellerId(SELLER_ID);
        w.setAmount(AMOUNT);
        w.setStatus(WithdrawalStatusEnum.PENDING_REVIEW.getCode());
        return w;
    }

    // ─────────────────────────── 申请侧 ───────────────────────────

    @Test
    @DisplayName("applyWithdrawal — 金额为 0/负数：PARAM_ERROR，不预占、不落库、不核销")
    void shouldReject_whenAmountNotPositive() {
        BizException ex = assertThrows(BizException.class,
                () -> manager.applyWithdrawal(SELLER_ID, req(0)));
        assertEquals(ErrorCode.PARAM_ERROR, ex.getErrorCode());
        assertThrows(BizException.class, () -> manager.applyWithdrawal(SELLER_ID, req(-100)));

        verify(fundAccountRepository, never()).freeze(anyLong(), anyInt());
        verify(withdrawalRepository, never()).save(any(Withdrawal.class));
        verify(withdrawalSettlementRepository, never()).allocate(anyLong(), any());
    }

    @Test
    @DisplayName("applyWithdrawal — 超过单笔上限：PARAM_ERROR（上限与余额无关，留方法体独立判）")
    void shouldReject_whenAmountExceedsSingleLimit() {
        BizException ex = assertThrows(BizException.class,
                () -> manager.applyWithdrawal(SELLER_ID, req(MAX_PER_REQUEST + 1)));
        assertEquals(ErrorCode.PARAM_ERROR, ex.getErrorCode());

        verify(fundAccountRepository, never()).freeze(anyLong(), anyInt());
        verify(withdrawalSettlementRepository, never()).allocate(anyLong(), any());
    }

    @Test
    @DisplayName("applyWithdrawal — 条件预占 0 行：BALANCE_INSUFFICIENT，不落库、不核销")
    void shouldReject_whenFreezeReturnsZero() {
        when(fundAccountRepository.freeze(SELLER_ID, AMOUNT)).thenReturn(0);

        BizException ex = assertThrows(BizException.class,
                () -> manager.applyWithdrawal(SELLER_ID, req(AMOUNT)));
        assertEquals(ErrorCode.BALANCE_INSUFFICIENT, ex.getErrorCode());

        verify(withdrawalRepository, never()).save(any(Withdrawal.class));
        verify(withdrawalSettlementRepository, never()).allocate(anyLong(), any());
    }

    @Test
    @DisplayName("applyWithdrawal — 正常：**冻结 → 落单 → 核销**（顺序写死），核销足额")
    void shouldFreezeThenSaveThenAllocate_whenAmountValid() {
        when(fundAccountRepository.freeze(SELLER_ID, AMOUNT)).thenReturn(1);
        when(withdrawalRepository.save(any(Withdrawal.class))).thenAnswer(invocation -> {
            Withdrawal saved = invocation.getArgument(0);
            saved.setId(WITHDRAWAL_ID);
            return saved;
        });
        when(settlementRepository.findAllocatable(SELLER_ID))
                .thenReturn(List.of(new SettlementRemaining(SETTLEMENT_ID, AMOUNT)));

        manager.applyWithdrawal(SELLER_ID, req(AMOUNT));

        InOrder order = inOrder(fundAccountRepository, withdrawalRepository, withdrawalSettlementRepository);
        order.verify(fundAccountRepository).freeze(SELLER_ID, AMOUNT);
        order.verify(withdrawalRepository).save(any(Withdrawal.class));
        order.verify(withdrawalSettlementRepository).allocate(WITHDRAWAL_ID,
                List.of(new SettlementAllocator.Allocation(SETTLEMENT_ID, AMOUNT)));

        ArgumentCaptor<Withdrawal> captor = ArgumentCaptor.forClass(Withdrawal.class);
        verify(withdrawalRepository).save(captor.capture());
        assertEquals(WithdrawalStatusEnum.PENDING_REVIEW.getCode(), captor.getValue().getStatus());
        assertNotNull(captor.getValue().getWithdrawalNo());
    }

    @Test
    @DisplayName("applyWithdrawal — 可核销额不足（规划合计 ≠ 金额）：BUSINESS_EXECUTION_EXCEPTION，不核销")
    void shouldFailClosed_whenAllocationPlanInsufficient() {
        when(fundAccountRepository.freeze(SELLER_ID, AMOUNT)).thenReturn(1);
        when(withdrawalRepository.save(any(Withdrawal.class))).thenAnswer(invocation -> {
            Withdrawal saved = invocation.getArgument(0);
            saved.setId(WITHDRAWAL_ID);
            return saved;
        });
        when(settlementRepository.findAllocatable(SELLER_ID))
                .thenReturn(List.of(new SettlementRemaining(SETTLEMENT_ID, AMOUNT - 1)));

        BizException ex = assertThrows(BizException.class,
                () -> manager.applyWithdrawal(SELLER_ID, req(AMOUNT)));
        assertEquals(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("核销"), "报错必须指向核销计划不足，而不是笼统的余额不足");

        verify(withdrawalSettlementRepository, never()).allocate(anyLong(), any());
    }

    // ─────────────────────────── 审核侧 ───────────────────────────

    @Test
    @DisplayName("review — 打款渠道未接入（默认关闭）：拒绝标已打款，避免「已记账未付款」")
    void shouldRejectPaidOut_whenPayoutChannelDisabled() {
        WithdrawalManager noChannel = new WithdrawalManager(withdrawalRepository, fundAccountRepository,
                settlementRepository, withdrawalSettlementRepository, settlementManager,
                new TradeConverterImpl());
        when(withdrawalRepository.findById(WITHDRAWAL_ID)).thenReturn(pendingWithdrawal());

        BizException ex = assertThrows(BizException.class,
                () -> noChannel.review(WITHDRAWAL_ID, WithdrawalStatusEnum.PAID_OUT.getCode(), "已打款"));
        assertEquals(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("渠道未接入"), "报错必须直说「没有打款渠道」，不能含糊成系统错误");

        verify(settlementManager, never()).payout(anyLong(), anyInt());
        verify(fundAccountRepository, never()).unfreeze(anyLong(), anyInt());
        // ★ 闸必须在状态迁移**之前**：否则靠「事务回滚」兜底，而回滚能否生效依赖代理
        verify(withdrawalRepository, never()).reviewIfPending(anyLong(), anyInt(), anyInt(), any());
    }

    @Test
    @DisplayName("review — 非法状态值：PARAM_ERROR，且不读库")
    void shouldRejectReview_whenStatusIllegal() {
        BizException ex = assertThrows(BizException.class, () -> manager.review(1L, 99, "备注"));
        assertEquals(ErrorCode.PARAM_ERROR, ex.getErrorCode());
        verify(withdrawalRepository, never()).findById(anyLong());
    }

    @Test
    @DisplayName("review — 目标非终态：PARAM_ERROR，且不读库")
    void shouldRejectReview_whenTargetNotTerminal() {
        BizException ex = assertThrows(BizException.class,
                () -> manager.review(1L, WithdrawalStatusEnum.PENDING_REVIEW.getCode(), "备注"));
        assertEquals(ErrorCode.PARAM_ERROR, ex.getErrorCode());
        verify(withdrawalRepository, never()).findById(anyLong());
    }

    @Test
    @DisplayName("review — 提现单不存在：WITHDRAWAL_NOT_FOUND，不动账户、不动核销")
    void shouldRejectReview_whenEntityMissing() {
        when(withdrawalRepository.findById(WITHDRAWAL_ID)).thenReturn(null);

        BizException ex = assertThrows(BizException.class,
                () -> manager.review(WITHDRAWAL_ID, WithdrawalStatusEnum.PAID_OUT.getCode(), "已打款"));
        assertEquals(ErrorCode.WITHDRAWAL_NOT_FOUND, ex.getErrorCode());

        verify(fundAccountRepository, never()).unfreeze(anyLong(), anyInt());
        verify(withdrawalSettlementRepository, never()).release(anyLong());
        verify(settlementManager, never()).payout(anyLong(), anyInt());
    }

    @Test
    @DisplayName("review — 打款：迁移 → 解冻 → 记账；**不退核销**（分配行保留给 E6）")
    void shouldReleaseThenPayout_whenReviewPaidOut() {
        when(withdrawalRepository.findById(WITHDRAWAL_ID)).thenReturn(pendingWithdrawal());
        when(withdrawalRepository.reviewIfPending(anyLong(), anyInt(), anyInt(), any())).thenReturn(1);
        when(fundAccountRepository.unfreeze(SELLER_ID, AMOUNT)).thenReturn(1);

        manager.review(WITHDRAWAL_ID, WithdrawalStatusEnum.PAID_OUT.getCode(), "已打款");

        InOrder order = inOrder(withdrawalRepository, fundAccountRepository, settlementManager);
        order.verify(withdrawalRepository).reviewIfPending(anyLong(), anyInt(), anyInt(), any());
        order.verify(fundAccountRepository).unfreeze(SELLER_ID, AMOUNT);
        order.verify(settlementManager).payout(SELLER_ID, AMOUNT);
        verify(withdrawalSettlementRepository, never()).release(anyLong());
    }

    @Test
    @DisplayName("review — 拒绝：迁移 → **退核销** → 解冻；不记账")
    void shouldReleaseAllocationThenUnfreeze_whenReviewRejected() {
        when(withdrawalRepository.findById(WITHDRAWAL_ID)).thenReturn(pendingWithdrawal());
        when(withdrawalRepository.reviewIfPending(anyLong(), anyInt(), anyInt(), any())).thenReturn(1);
        when(fundAccountRepository.unfreeze(SELLER_ID, AMOUNT)).thenReturn(1);

        manager.review(WITHDRAWAL_ID, WithdrawalStatusEnum.REJECTED.getCode(), "信息不全");

        InOrder order = inOrder(withdrawalRepository, withdrawalSettlementRepository, fundAccountRepository);
        order.verify(withdrawalRepository).reviewIfPending(anyLong(), anyInt(), anyInt(), any());
        order.verify(withdrawalSettlementRepository).release(WITHDRAWAL_ID);
        order.verify(fundAccountRepository).unfreeze(SELLER_ID, AMOUNT);
        verify(settlementManager, never()).payout(anyLong(), anyInt());
    }

    @Test
    @DisplayName("review — 重复审核（迁移 0 行）：幂等返回，核销/冻结/记账全都不许动")
    void shouldNotTouchAnything_whenReviewReplayed() {
        when(withdrawalRepository.findById(WITHDRAWAL_ID)).thenReturn(pendingWithdrawal());
        when(withdrawalRepository.reviewIfPending(anyLong(), anyInt(), anyInt(), any())).thenReturn(0);

        manager.review(WITHDRAWAL_ID, WithdrawalStatusEnum.REJECTED.getCode(), "再点一次");

        verify(withdrawalSettlementRepository, never()).release(anyLong());
        verify(fundAccountRepository, never()).unfreeze(anyLong(), anyInt());
        verify(settlementManager, never()).payout(anyLong(), anyInt());
    }

    @Test
    @DisplayName("review — 解冻 0 行（冻结额不足=账不平）：抛异常回滚，不记账")
    void shouldThrow_whenUnfreezeMisses() {
        when(withdrawalRepository.findById(WITHDRAWAL_ID)).thenReturn(pendingWithdrawal());
        when(withdrawalRepository.reviewIfPending(anyLong(), anyInt(), anyInt(), any())).thenReturn(1);
        when(fundAccountRepository.unfreeze(SELLER_ID, AMOUNT)).thenReturn(0);

        BizException ex = assertThrows(BizException.class,
                () -> manager.review(WITHDRAWAL_ID, WithdrawalStatusEnum.PAID_OUT.getCode(), "已打款"));
        assertEquals(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, ex.getErrorCode());

        verify(settlementManager, never()).payout(anyLong(), anyInt());
    }

    @Test
    @DisplayName("review — 审核侧不判额度：不读 availableBalance")
    void shouldNotCheckAvailableBalance_whenReviewPaidOut() {
        when(withdrawalRepository.findById(WITHDRAWAL_ID)).thenReturn(pendingWithdrawal());
        when(withdrawalRepository.reviewIfPending(anyLong(), anyInt(), anyInt(), any())).thenReturn(1);
        when(fundAccountRepository.unfreeze(SELLER_ID, AMOUNT)).thenReturn(1);

        manager.review(WITHDRAWAL_ID, WithdrawalStatusEnum.PAID_OUT.getCode(), "已打款");

        verify(settlementManager, never()).availableBalance(anyLong());
        verify(settlementManager).payout(SELLER_ID, AMOUNT);
    }
}
