package org.icedamericanomall.trade.app.finance;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.trade.domain.enums.WithdrawalStatusEnum;
import org.icedamericanomall.trade.domain.model.finance.Withdrawal;
import org.icedamericanomall.trade.domain.port.WithdrawalPayoutPort;
import org.icedamericanomall.trade.domain.repository.WithdrawalRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 出款执行器：扫「付款中」的单，调渠道，拿回执后落账。
 *
 * <p><b>为什么是独立一类而不是塞进 {@code WithdrawalManager}</b>：
 * 这里要调渠道（远程），而 {@code review} 必须在事务里 —— 两者的事务语义相反，
 * 混在一个类里迟早会把远程调用带进事务（本仓硬约束：调外部接口的方法不得 {@code @Transactional}）。
 * 而且 {@code review} → {@code settle} 若是同类自调用，事务注解会失效（守卫 {@code TransactionSelfInvocationGuardTest}）。
 *
 * <p><b>失败语义</b>：渠道未受理（{@code accepted=false}）时<b>保持「付款中」</b>，只告警。
 * 「结果未知」不是「失败」—— 判失败会引出「回执丢了 → 重试 → 出两次钱」或「释放额度但钱已出」。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WithdrawalPayoutService {

    /** 单次扫描上限（防一次任务拉爆渠道配额）。 */
    private static final int DEFAULT_LIMIT = 100;

    private final WithdrawalRepository withdrawalRepository;
    private final WithdrawalPayoutPort payoutPort;
    private final WithdrawalManager withdrawalManager;

    /**
     * 执行一次出款扫描（<b>不在事务里</b>：内部调渠道）。
     *
     * @param limit 扫描上限
     * @return 本次成功落账的单数
     */
    public int settle(int limit) {
        List<Long> payingIds = withdrawalRepository.findPayingIds(limit);
        int settled = 0;
        for (Long id : payingIds) {
            if (settleOne(id)) {
                settled++;
            }
        }
        return settled;
    }

    private boolean settleOne(Long id) {
        Withdrawal withdrawal = withdrawalRepository.findById(id);
        if (withdrawal == null) {
            log.warn("[payout] 付款中的单查不到，跳过 id={}", id);
            return false;
        }
        int amount = withdrawal.getAmount() == null ? 0 : withdrawal.getAmount();
        // 重试复用同一个 payoutNo（withdrawalNo）：渠道侧去重的唯一依据
        WithdrawalPayoutPort.PayoutResult result = payoutPort.pay(new WithdrawalPayoutPort.PayoutCommand(
                withdrawal.getWithdrawalNo(), withdrawal.getId(), withdrawal.getSellerId(), amount,
                withdrawal.getBankAccount(), withdrawal.getBankName()));
        if (!result.accepted()) {
            // 结果未知 ⇒ 保持 PAYING + 告警；**不判失败、不释放冻结**
            log.warn("[payout] 渠道未受理，保持付款中（结果未知≠失败） id={} reason={}", id, result.reason());
            return false;
        }
        if (!withdrawalManager.completePayout(withdrawal)) {
            log.warn("[payout] 渠道已受理但状态已变，未记账（幂等） id={} 当前状态={}",
                    id, WithdrawalStatusEnum.PAYING.getCode());
            return false;
        }
        return true;
    }
}
