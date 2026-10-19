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
import org.icedamericanomall.trade.domain.repository.SellerFundAccountRepository;
import org.icedamericanomall.trade.domain.repository.SettlementRepository;
import org.icedamericanomall.trade.domain.port.WithdrawalPayoutPort;
import org.icedamericanomall.trade.domain.repository.WithdrawalSettlementRepository;
import org.icedamericanomall.trade.domain.service.SettlementAllocator;
import org.icedamericanomall.trade.domain.repository.WithdrawalRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 提现用例族编排（应用层）。
 *
 * <pre>
 * Scenario: 商家申请提现
 *   Given 提现额大于 0、不超过单笔上限、且不超过可用余额
 *   When 提交申请
 *   Then 条件预占 frozen_amount（0 行 = 额度不足 → 拒单，不落库）并落库"待审核"提现单（同一事务）
 * Scenario: 金额非法 / 超单笔上限 → PARAM_ERROR；可用额不足 → BALANCE_INSUFFICIENT
 * Scenario: 管理员审核 → 条件迁移 + 释放预占（PAID_OUT 另记 paid_total），同一事务
 * Scenario: 审核非法状态值 / 非终态目标 → PARAM_ERROR
 * </pre>
 */
@Component
@RequiredArgsConstructor
public class WithdrawalManager {

    private final WithdrawalRepository withdrawalRepository;
    private final SellerFundAccountRepository sellerFundAccountRepository;
    private final SettlementRepository settlementRepository;
    private final WithdrawalSettlementRepository withdrawalSettlementRepository;
    private final WithdrawalPayoutPort payoutPort;
    private final SettlementManager settlementManager;
    private final TradeConverter tradeConverter;

    /**
     * 单笔上限（分）。与<b>可用额</b>是两个判断，别合并：
     * <ul>
     *   <li>可用额（{@code settled_total − frozen − paid ≥ amount}）必须进 SQL 的 {@code WHERE}（原子性），</li>
     *   <li>单笔上限只依赖请求本身、与账户状态无关、无竞态 ⇒ 留在方法体里早失败。</li>
     * </ul>
     * 默认 50000.00 元；按业务调整配置，不要为了绕开它去改额度（那会让 SQL 条件失去意义）。
     */
    @Value("${trade.withdrawal.max-per-request:5000000}")
    private int maxPerRequest = 5_000_000;

    /**
     * 打款渠道是否已接入。<b>默认 false</b>：此时「审核为已打款」<b>直接拒绝</b>。
     *
     * <p>为什么默认关：这个动作只改本地状态 + 记 {@code paid_total}，**不调用任何出款接口** ——
     * 是「已记账未付款」，比「功能不可用」更危险（账上有、钱没出，商家投诉时无从自证）。
     * 人工银行转账场景请显式打开 {@code trade.withdrawal.payout-enabled=true}，并明白
     * 「系统认为已付款」来自人工确认，不是渠道回执。
     */
    @Value("${trade.withdrawal.payout-enabled:false}")
    private boolean payoutEnabled;

    /** 商家提现单分页（创建时间倒序）。 */
    public IPage<WithdrawalVO> pageBySeller(Long sellerId, int page, int size) {
        return withdrawalRepository.pageBySeller(sellerId, page, size)
                .convert(tradeConverter::toSellerVO);
    }

    /** 管理端提现单分页（可按状态过滤；管理端保留明文账号打款核验）。 */
    public IPage<WithdrawalVO> pageByStatus(Integer status, int page, int size) {
        return withdrawalRepository.pageByStatus(status, page, size).convert(tradeConverter::toVO);
    }

    /**
     * 商家申请提现：<b>条件预占</b> + 落库「待审核」，同一事务。
     *
     * <p><b>顺序是「先预占、再插单」</b>（不是风格问题）：预占是唯一能原子守额度的地方 ——
     * {@code UPDATE ... WHERE settled_total - frozen_amount - paid_total >= amount}，0 行 = 额度不足 ⇒
     * 直接拒单，这张单还没落库，不会留下「有单没冻结」的脏数据。
     * 反过来先插单再预占，就要靠抛异常回滚，且很容易被后来者改成「0 行就 return」。
     *
     * <p><b>额度判定只在申请侧</b>（审核侧不判，见 {@link #review}）：
     * 读-比较-写不是原子的，两笔并发各自读到同一可用额会同时通过 —— 所以额度必须写进 WHERE。
     */
    @Transactional(rollbackFor = Exception.class)
    public WithdrawalVO applyWithdrawal(Long sellerId, WithdrawalApplyReq req) {
        Integer amount = req.getAmount();
        if (amount == null || amount <= 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "提现金额必须大于 0");
        }
        if (amount > maxPerRequest) {
            throw new BizException(ErrorCode.PARAM_ERROR, "单笔提现金额超过上限");
        }
        if (sellerFundAccountRepository.freeze(sellerId, amount) == 0) {
            throw new BizException(ErrorCode.BALANCE_INSUFFICIENT, "可用余额不足（可能已有待审提现占用）");
        }
        Withdrawal withdrawal = new Withdrawal();
        withdrawal.setSellerId(sellerId);
        withdrawal.setWithdrawalNo(IdGenerator.nextId());
        withdrawal.setAmount(amount);
        withdrawal.setBankAccount(req.getBankAccount());
        withdrawal.setBankName(req.getBankName());
        withdrawal.setStatus(WithdrawalStatusEnum.PENDING_REVIEW.getCode());
        Withdrawal saved = withdrawalRepository.save(withdrawal);

        // 核销计划必须在「冻结成功」之后、且在「明细落库」之前算：冻结守住账户维度，核销钉死单据维度。
        // 两者是同一件事的两个投影（E3 与 E5），任一步失败都整笔回滚。
        List<SettlementAllocator.Allocation> plan =
                SettlementAllocator.fifo(amount, settlementRepository.findAllocatable(sellerId));
        if (SettlementAllocator.sum(plan) != amount) {
            // 失败关闭：账户额度够而单据余额不够 = 数据漂移（E4/E5 已破），不许「少核销一点」蒙过去
            throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION,
                    "核销计划不足：可核销结算单余额小于提现金额（数据漂移，需对账）");
        }
        withdrawalSettlementRepository.allocate(saved.getId(), plan);
        return tradeConverter.toSellerVO(saved);
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
    /**
     * 管理员审核提现：<b>条件迁移</b> → <b>释放预占</b> → （已打款时）记账，全部同一事务。
     *
     * <p><b>顺序是「先迁移、后动账」，这条顺序被幂等语义决定，不是风格</b>：
     * 迁移 0 行 = 重复审核/并发双击 ⇒ 先返回，<b>不许动账户</b>。
     * 若写反（先扣 {@code frozen} 再迁移），重复审核会先扣一次再看 0 行返回 —— 凭空吃钱。
     *
     * <p>释放预占 0 行 = 冻结额不足 ⇒ <b>账不平</b> ⇒ 抛异常整笔回滚（此时状态已变，不能静默）。
     * 这是第二步新增的守门：{@code frozen_amount} 的减少一律带 {@code WHERE frozen_amount >= amount}。
     *
     * <p><b>审核侧仍不判额度</b>（写死，别顺手加回来）：额度属于<b>申请侧</b>
     * {@code applyWithdrawal}（条件预占 {@code frozen_amount}）。审核只在断言「钱已按申请时的额度打出去了」；
     * 在这里再判一次会制造假拒绝 —— 申请时通过、打款时因期间余额变化被拒，而钱其实已经付出去了。
     *
     * <p><b>不回写结算单状态</b>：{@code settlement.status} 的 {@code SETTLED} 只表示「可提现」，**不承载打款事实**；
     * 「已打款」的唯一事实源是 {@code withdrawal.status = PAID_OUT}（聚合看 {@code seller_fund_account.paid_total}）。
     * 要让结算单列表显示「已打款」，属<b>读模型</b>（join {@code withdrawal}），不是改行状态。
     */
    @Transactional(rollbackFor = Exception.class)
    public void review(Long id, Integer status, String adminRemark) {
        WithdrawalStatusEnum target = WithdrawalStatusEnum.of(status);
        if (target != WithdrawalStatusEnum.PAID_OUT && target != WithdrawalStatusEnum.REJECTED) {
            throw new BizException(ErrorCode.PARAM_ERROR, "审核目标状态只能是「已打款」或「已拒绝」: " + status);
        }
        Withdrawal withdrawal = withdrawalRepository.findById(id);
        if (withdrawal == null) {
            throw new BizException(ErrorCode.WITHDRAWAL_NOT_FOUND, "提现申请不存在");
        }
        // ⚠ 打款闸必须在**状态迁移之前**：迁移之后再抛，靠的是「事务回滚」兜底；
        // 而事务能否生效取决于代理（自调用就没了），且本仓测试 harness 无法验证回滚。
        // 放在这里 = 压根不改状态，不依赖任何回滚能力。
        if (target == WithdrawalStatusEnum.PAID_OUT && !payoutEnabled) {
            throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION,
                    "打款渠道未接入：本系统只记账不付款，禁止把提现标成已打款（trade.withdrawal.payout-enabled）");
        }
        int claimed = withdrawalRepository.reviewIfPending(id,
                WithdrawalStatusEnum.PENDING_REVIEW.getCode(), target.getCode(), adminRemark);
        if (claimed == 0) {
            return;     // 重复审核 ⇒ 幂等，且**不动账户**（先迁移后动账的唯一理由）
        }
        int amount = withdrawal.getAmount() == null ? 0 : withdrawal.getAmount();
        if (target == WithdrawalStatusEnum.REJECTED) {
            // 释放两个维度：先退回单据核销（LIFO 反向），再解冻账户额度
            withdrawalSettlementRepository.release(withdrawal.getId());
        }
        releaseFrozen(withdrawal.getSellerId(), amount);
        if (target == WithdrawalStatusEnum.PAID_OUT) {
            // 出款只走端口（当前为「人工确认」适配器，无 I/O）；受理失败**不得**标已打款
            WithdrawalPayoutPort.PayoutResult result = payoutPort.pay(new WithdrawalPayoutPort.PayoutCommand(
                    withdrawal.getWithdrawalNo(), withdrawal.getId(), withdrawal.getSellerId(), amount,
                    withdrawal.getBankAccount(), withdrawal.getBankName()));
            if (!result.accepted()) {
                throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION,
                        "出款未受理：" + result.reason());
            }
            settlementManager.payout(withdrawal.getSellerId(), amount);
        }
    }

    /** 释放预占；0 行 = 冻结额不足（账不平）⇒ 抛异常让整笔回滚，不许 return。 */
    private void releaseFrozen(Long sellerId, int amount) {
        if (amount <= 0) {
            return;     // 申请侧已拒 0 额，历史脏数据不该让这里炸
        }
        if (sellerFundAccountRepository.unfreeze(sellerId, amount) == 0) {
            throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION,
                    "预占释放失败：冻结额不足，账不平（sellerId=" + sellerId + ", amount=" + amount + "）");
        }
    }
}
