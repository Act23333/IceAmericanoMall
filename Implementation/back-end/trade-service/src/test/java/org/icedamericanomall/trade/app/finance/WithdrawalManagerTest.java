package org.icedamericanomall.trade.app.finance;

import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.trade.adapter.assembler.TradeConverterImpl;
import org.icedamericanomall.trade.adapter.request.WithdrawalApplyReq;
import org.icedamericanomall.trade.domain.enums.WithdrawalStatusEnum;
import org.icedamericanomall.trade.domain.model.finance.Withdrawal;
import org.icedamericanomall.trade.domain.repository.SellerFundAccountRepository;
import org.icedamericanomall.trade.domain.repository.WithdrawalRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@code WithdrawalManager} 单测 —— <b>第二步</b>起它多了两件必须守的行为：
 * 申请侧「条件预占」（额度进 SQL 的 WHERE），审核侧「先迁移状态、后释放预占」。
 *
 * <p><b>三处「不许顺手加回来 / 改顺序」</b>：
 * <ol>
 *   <li>审核侧<b>不判额度</b>：额度属申请侧（{@code applyWithdrawal} 的条件预占）；
 *       审核再判一次会制造「申请通过、打款被拒，而钱已付出」的假拒绝。</li>
 *   <li>审核<b>先迁移后动账</b>：迁移 0 行 = 重复审核 ⇒ 幂等返回、<b>不许碰 {frozen}</b>。
 *       写反会让重复审核先扣一次冻结额再看 0 行返回 —— 凭空吃钱。</li>
 *   <li>释放预占 0 行 = <b>账不平</b> ⇒ 抛异常回滚，不许 {@code return} 了事。</li>
 * </ol>
 * 真实 SQL 行为（额度不足/冻结额不足）由 {@code SettlementFundAccountH2Test} 用真库证，
 * 这里只验编排顺序与分支。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("WithdrawalManager 提现校验单元测试")
class WithdrawalManagerTest {

    private static final long SELLER_ID = 9L;
    private static final int AMOUNT = 5000;
    /** 与 {@code WithdrawalManager#maxPerRequest} 的默认值一致（手动构造时注入不生效，用默认）。 */
    private static final int MAX_PER_REQUEST = 5_000_000;

    @Mock private SettlementManager settlementManager;
    @Mock private WithdrawalRepository withdrawalRepository;
    @Mock private SellerFundAccountRepository fundAccountRepository;

    private WithdrawalManager manager;

    @BeforeEach
    void setUp() {
        manager = new WithdrawalManager(withdrawalRepository, fundAccountRepository,
                settlementManager, new TradeConverterImpl());
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

    // ─────────────────────────── 申请侧 ───────────────────────────

    @Test
    @DisplayName("applyWithdrawal — 金额为 0/负数：PARAM_ERROR，不预占、不落库")
    void shouldReject_whenAmountNotPositive() {
        BizException ex = assertThrows(BizException.class,
                () -> manager.applyWithdrawal(SELLER_ID, req(0)));
        assertEquals(ErrorCode.PARAM_ERROR, ex.getErrorCode());
        assertThrows(BizException.class, () -> manager.applyWithdrawal(SELLER_ID, req(-100)));

        verify(fundAccountRepository, never()).freeze(anyLong(), anyInt());
        verify(withdrawalRepository, never()).save(any(Withdrawal.class));
    }

    @Test
    @DisplayName("applyWithdrawal — 超过单笔上限：PARAM_ERROR（上限与余额无关，留在方法体独立判）")
    void shouldReject_whenAmountExceedsSingleLimit() {
        BizException ex = assertThrows(BizException.class,
                () -> manager.applyWithdrawal(SELLER_ID, req(MAX_PER_REQUEST + 1)));
        assertEquals(ErrorCode.PARAM_ERROR, ex.getErrorCode());

        verify(fundAccountRepository, never()).freeze(anyLong(), anyInt());
        verify(withdrawalRepository, never()).save(any(Withdrawal.class));
    }

    @Test
    @DisplayName("applyWithdrawal — 条件预占 0 行（额度不足）：BALANCE_INSUFFICIENT，且不落库")
    void shouldReject_whenFreezeReturnsZero() {
        when(fundAccountRepository.freeze(SELLER_ID, AMOUNT)).thenReturn(0);

        BizException ex = assertThrows(BizException.class,
                () -> manager.applyWithdrawal(SELLER_ID, req(AMOUNT)));
        assertEquals(ErrorCode.BALANCE_INSUFFICIENT, ex.getErrorCode());

        verify(withdrawalRepository, never()).save(any(Withdrawal.class));
    }

    @Test
    @DisplayName("applyWithdrawal — 预占成功：**先预占、后落库**（顺序写死），落「待审核」单")
    void shouldFreezeThenSave_whenAmountValid() {
        when(fundAccountRepository.freeze(SELLER_ID, AMOUNT)).thenReturn(1);
        when(withdrawalRepository.save(any(Withdrawal.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        manager.applyWithdrawal(SELLER_ID, req(AMOUNT));

        InOrder order = inOrder(fundAccountRepository, withdrawalRepository);
        order.verify(fundAccountRepository).freeze(SELLER_ID, AMOUNT);
        order.verify(withdrawalRepository).save(any(Withdrawal.class));

        ArgumentCaptor<Withdrawal> captor = ArgumentCaptor.forClass(Withdrawal.class);
        verify(withdrawalRepository).save(captor.capture());
        Withdrawal saved = captor.getValue();
        assertEquals(WithdrawalStatusEnum.PENDING_REVIEW.getCode(), saved.getStatus());
        assertEquals(AMOUNT, saved.getAmount());
        assertNotNull(saved.getWithdrawalNo());
    }

    // ─────────────────────────── 审核侧 ───────────────────────────

    @Test
    @DisplayName("review — 非法状态值：PARAM_ERROR，且不读库")
    void shouldRejectReview_whenStatusIllegal() {
        BizException ex = assertThrows(BizException.class, () -> manager.review(1L, 99, "备注"));
        assertEquals(ErrorCode.PARAM_ERROR, ex.getErrorCode());
        verify(withdrawalRepository, never()).findById(anyLong());
    }

    @Test
    @DisplayName("review — 目标非终态（如仍为待审核）：PARAM_ERROR，且不读库")
    void shouldRejectReview_whenTargetNotTerminal() {
        BizException ex = assertThrows(BizException.class,
                () -> manager.review(1L, WithdrawalStatusEnum.PENDING_REVIEW.getCode(), "备注"));
        assertEquals(ErrorCode.PARAM_ERROR, ex.getErrorCode());
        verify(withdrawalRepository, never()).findById(anyLong());
    }

    @Test
    @DisplayName("review — 提现单不存在：WITHDRAWAL_NOT_FOUND，不动账户")
    void shouldRejectReview_whenEntityMissing() {
        when(withdrawalRepository.findById(1L)).thenReturn(null);

        BizException ex = assertThrows(BizException.class,
                () -> manager.review(1L, WithdrawalStatusEnum.PAID_OUT.getCode(), "已打款"));
        assertEquals(ErrorCode.WITHDRAWAL_NOT_FOUND, ex.getErrorCode());

        verify(fundAccountRepository, never()).unfreeze(anyLong(), anyInt());
        verify(settlementManager, never()).payout(anyLong(), anyInt());
    }

    @Test
    @DisplayName("review — 打款：**先迁移状态、再释放预占、最后记账**（顺序写死）")
    void shouldReleaseThenPayout_whenReviewPaidOut() {
        Withdrawal w = pendingWithdrawal();
        when(withdrawalRepository.findById(1L)).thenReturn(w);
        when(withdrawalRepository.reviewIfPending(1L,
                WithdrawalStatusEnum.PENDING_REVIEW.getCode(), WithdrawalStatusEnum.PAID_OUT.getCode(),
                "已打款")).thenReturn(1);
        when(fundAccountRepository.unfreeze(SELLER_ID, AMOUNT)).thenReturn(1);

        manager.review(1L, WithdrawalStatusEnum.PAID_OUT.getCode(), "已打款");

        InOrder order = inOrder(withdrawalRepository, fundAccountRepository, settlementManager);
        order.verify(withdrawalRepository).reviewIfPending(1L,
                WithdrawalStatusEnum.PENDING_REVIEW.getCode(), WithdrawalStatusEnum.PAID_OUT.getCode(),
                "已打款");
        order.verify(fundAccountRepository).unfreeze(SELLER_ID, AMOUNT);
        order.verify(settlementManager).payout(SELLER_ID, AMOUNT);
    }

    @Test
    @DisplayName("review — 拒绝：释放预占但**不记账**（paid_total 不动）")
    void shouldReleaseOnly_whenReviewRejected() {
        Withdrawal w = pendingWithdrawal();
        when(withdrawalRepository.findById(1L)).thenReturn(w);
        when(withdrawalRepository.reviewIfPending(1L,
                WithdrawalStatusEnum.PENDING_REVIEW.getCode(), WithdrawalStatusEnum.REJECTED.getCode(),
                "信息不全")).thenReturn(1);
        when(fundAccountRepository.unfreeze(SELLER_ID, AMOUNT)).thenReturn(1);

        manager.review(1L, WithdrawalStatusEnum.REJECTED.getCode(), "信息不全");

        verify(fundAccountRepository).unfreeze(SELLER_ID, AMOUNT);
        verify(settlementManager, never()).payout(anyLong(), anyInt());
    }

    @Test
    @DisplayName("review — 重复审核（条件迁移 0 行）：幂等返回，**冻结额与 paid_total 都不许动**")
    void shouldNotTouchAccount_whenReviewReplayed() {
        Withdrawal w = pendingWithdrawal();
        when(withdrawalRepository.findById(1L)).thenReturn(w);
        when(withdrawalRepository.reviewIfPending(anyLong(), anyInt(), anyInt(), any())).thenReturn(0);

        manager.review(1L, WithdrawalStatusEnum.PAID_OUT.getCode(), "已打款");

        // 这条是「先迁移后动账」的守门用例：顺序写反时，重复审核会先扣一次 froze 再看 0 行返回
        verify(fundAccountRepository, never()).unfreeze(anyLong(), anyInt());
        verify(settlementManager, never()).payout(anyLong(), anyInt());
    }

    @Test
    @DisplayName("review — 释放预占 0 行（冻结额不足=账不平）：抛异常让整笔回滚，且不记账")
    void shouldThrow_whenUnfreezeMisses() {
        Withdrawal w = pendingWithdrawal();
        when(withdrawalRepository.findById(1L)).thenReturn(w);
        when(withdrawalRepository.reviewIfPending(anyLong(), anyInt(), anyInt(), any())).thenReturn(1);
        when(fundAccountRepository.unfreeze(SELLER_ID, AMOUNT)).thenReturn(0);

        BizException ex = assertThrows(BizException.class,
                () -> manager.review(1L, WithdrawalStatusEnum.PAID_OUT.getCode(), "已打款"));
        assertEquals(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, ex.getErrorCode());

        verify(settlementManager, never()).payout(anyLong(), anyInt());
    }

    @Test
    @DisplayName("review — 审核侧不判额度：不读 availableBalance（额度属申请侧的条件预占）")
    void shouldNotCheckAvailableBalance_whenReviewPaidOut() {
        Withdrawal w = pendingWithdrawal();
        when(withdrawalRepository.findById(1L)).thenReturn(w);
        when(withdrawalRepository.reviewIfPending(anyLong(), anyInt(), anyInt(), any())).thenReturn(1);
        when(fundAccountRepository.unfreeze(SELLER_ID, AMOUNT)).thenReturn(1);

        manager.review(1L, WithdrawalStatusEnum.PAID_OUT.getCode(), "已打款");

        verify(settlementManager, never()).availableBalance(anyLong());
        verify(settlementManager).payout(SELLER_ID, AMOUNT);
    }
}
