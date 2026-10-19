package org.icedamericanomall.trade.app.finance;

import org.icedamericanomall.common.utils.IdGenerator;
import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.RequiredArgsConstructor;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.trade.adapter.assembler.TradeConverter;
import org.icedamericanomall.trade.adapter.request.WithdrawalApplyReq;
import org.icedamericanomall.trade.adapter.response.WithdrawalVO;
import org.icedamericanomall.trade.domain.enums.WithdrawalStatusEnum;
import org.icedamericanomall.trade.domain.model.finance.Withdrawal;
import org.icedamericanomall.trade.domain.repository.WithdrawalRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 提现用例族编排（应用层）。
 *
 * <pre>
 * Scenario: 商家申请提现
 *   Given 提现额大于 0 且不超过可用余额（已结算未打款金额）
 *   When 提交申请
 *   Then 落库"待审核"提现单
 * Scenario: 金额非法 / 超出可用余额 → PARAM_ERROR / BALANCE_INSUFFICIENT
 * Scenario: 管理员审核非法状态值 → PARAM_ERROR
 * </pre>
 */
@Component
@RequiredArgsConstructor
public class WithdrawalManager {

    private final WithdrawalRepository withdrawalRepository;
    private final SettlementManager settlementManager;
    private final TradeConverter tradeConverter;

    /** 商家提现单分页（创建时间倒序）。 */
    public IPage<WithdrawalVO> pageBySeller(Long sellerId, int page, int size) {
        return withdrawalRepository.pageBySeller(sellerId, page, size)
                .convert(tradeConverter::toSellerVO);
    }

    /** 管理端提现单分页（可按状态过滤；管理端保留明文账号打款核验）。 */
    public IPage<WithdrawalVO> pageByStatus(Integer status, int page, int size) {
        return withdrawalRepository.pageByStatus(status, page, size).convert(tradeConverter::toVO);
    }

    /** 商家申请提现。 */
    @Transactional(rollbackFor = Exception.class)
    public WithdrawalVO applyWithdrawal(Long sellerId, WithdrawalApplyReq req) {
        Integer amount = req.getAmount();
        if (amount == null || amount <= 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "提现金额必须大于 0");
        }
        int available = settlementManager.availableBalance(sellerId);
        if (amount > available) {
            throw new BizException(ErrorCode.BALANCE_INSUFFICIENT, "提现金额超过可用余额");
        }
        Withdrawal withdrawal = new Withdrawal();
        withdrawal.setSellerId(sellerId);
        withdrawal.setWithdrawalNo(IdGenerator.nextId());
        withdrawal.setAmount(amount);
        withdrawal.setBankAccount(req.getBankAccount());
        withdrawal.setBankName(req.getBankName());
        withdrawal.setStatus(WithdrawalStatusEnum.PENDING_REVIEW.getCode());
        return tradeConverter.toSellerVO(withdrawalRepository.save(withdrawal));
    }

    /**
     * 管理员审核提现；打款成功时在权威资金账户上记账（{@code paid_total += amount}）。
     *
     * <p><b>两步，且必须同一事务</b>：
     * <ol>
     *   <li><b>条件迁移</b>「待审核 → 目标状态」（{@code WHERE id=? AND status=?}，不读-改-写）。
     *       0 行 = 重复审核/并发双击 ⇒ <b>幂等返回，不重复记账</b> —— 否则同一笔打款会把 {@code paid_total}
     *       记两遍（余额被悄悄压低，且只能靠对账发现）。这是审核侧**唯一**的守门条件。</li>
     *   <li>目标为「已打款」时记账。</li>
     * </ol>
     *
     * <p><b>审核侧不判额度</b>（写死，别顺手加回来）：额度「可提现额 ≥ amount」属于<b>申请侧</b>
     * {@code applyWithdrawal}（第二步改成条件更新预占 {@code frozen_amount}）。
     * 审核只是在断言「钱已按申请时的额度打出去了」；在这里再判一次会制造假拒绝 ——
     * 申请时通过、打款时因期间余额变化被拒，而钱其实已经付出去了。
     *
     * <p><b>不回写结算单状态</b>：`settlement.status` 的 `SETTLED` 只表示「可提现」，**不承载打款事实** ——
     * 「已打款」的唯一事实源就是本张 <b>{@code withdrawal} 表</b>（`status = PAID_OUT`，聚合看
     * {@code seller_fund_account.paid_total}）。旧 {@code markSellerSettlementsPaidOut} 的「按商家全量置位」
     * 已删除且不打算恢复（T-WITHDRAW-2：提现 100 会把 1000 全标成已打款、剩 900 看似再也提不出来）。
     * 要让结算单列表显示「已打款」，属**读模型**（join `withdrawal`），不是改行状态。
     */
    @Transactional(rollbackFor = Exception.class)
    public void review(Long id, Integer status, String adminRemark) {
        WithdrawalStatusEnum target = WithdrawalStatusEnum.of(status);
        if (target == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "非法提现状态: " + status);
        }
        Withdrawal withdrawal = withdrawalRepository.findById(id);
        if (withdrawal == null) {
            throw new BizException(ErrorCode.WITHDRAWAL_NOT_FOUND, "提现申请不存在");
        }
        int claimed = withdrawalRepository.reviewIfPending(id,
                WithdrawalStatusEnum.PENDING_REVIEW.getCode(), target.getCode(), adminRemark);
        if (claimed == 0) {
            return;
        }
        if (target == WithdrawalStatusEnum.PAID_OUT) {
            settlementManager.payout(withdrawal.getSellerId(),
                    withdrawal.getAmount() == null ? 0 : withdrawal.getAmount());
        }
    }
}
