package org.icedamericanomall.trade.app.finance;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.trade.domain.enums.WithdrawalStatusEnum;
import org.icedamericanomall.trade.domain.model.finance.Withdrawal;
import org.icedamericanomall.trade.domain.port.WithdrawalPayoutPort;
import org.icedamericanomall.trade.domain.repository.WithdrawalRepository;
import org.springframework.stereotype.Service;

import java.time.Clock;
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
public class WithdrawalPayoutService {

    /** 单次扫描上限（防一次任务拉爆渠道配额）。 */
    private static final int DEFAULT_LIMIT = 100;

    private final WithdrawalRepository withdrawalRepository;
    private final WithdrawalPayoutPort payoutPort;
    private final WithdrawalManager withdrawalManager;
    private final java.time.Clock clock;

    /**
     * 重试节流窗口（分钟）：与停留告警同一个配置项 —— 同一张单不会在窗口内被反复打向渠道。
     */
    @org.springframework.beans.factory.annotation.Value("${trade.withdrawal.payout.stale-alert-minutes:30}")
    private int retryIntervalMinutes = 30;

    /** 与 DB 列 `last_payout_result VARCHAR(160)` 对齐（留余量，应对更长渠道号）。 */
    static final int MAX_RESULT_LEN = 160;

    /**
     * 结果摘要：{@code 分类:渠道号}，列长 128。
     *
     * <p><b>超长时保留尾部</b>：渠道流水号里有用的部分（唯一/可检索的那段）通常在末尾，
     * 截头会留下"大家都一样的前缀"，反而不可用。截断本身是**防御性代码** ——
     * 已知渠道流水号不超此长度，这条只在换渠道/异常报文时兜底。
     */
    static String summarize(WithdrawalPayoutPort.PayoutResult result) {
        String head = result.accepted() ? "ACCEPTED:" : "REJECTED:";
        String tail = result.accepted() ? result.channelRef() : result.reason();
        String raw = head + tail;
        if (raw.length() <= MAX_RESULT_LEN) {
            return raw;
        }
        int keep = MAX_RESULT_LEN - head.length() - 3;
        String kept = tail.length() <= keep ? tail : tail.substring(tail.length() - keep);
        return head + "..." + kept;
    }

    /**
     * 最近一次出款结果（**读库**，重启后仍可读）。
     *
     * @param payoutNo 出款幂等键
     * @return 形如 {@code ACCEPTED:MOCK-WD-1}；从未尝试返回 NOT_ATTEMPTED
     */
    public String lastResult(String payoutNo) {
        String stored = withdrawalRepository.findPayoutResult(payoutNo);
        return stored == null ? "NOT_ATTEMPTED" : stored;
    }

    public WithdrawalPayoutService(WithdrawalRepository withdrawalRepository,
                                    WithdrawalPayoutPort payoutPort,
                                    WithdrawalManager withdrawalManager,
                                    org.springframework.beans.factory.ObjectProvider<Clock> clockProvider) {
        this(withdrawalRepository, payoutPort, withdrawalManager,
                clockProvider.getIfAvailable(Clock::systemUTC));
    }

    /**
     * 测试用：显式时钟 —— 必须与停留告警用**同一个** Clock 实例（§1.48 ④）。
     */
    public WithdrawalPayoutService(WithdrawalRepository withdrawalRepository,
                                   WithdrawalPayoutPort payoutPort,
                                   WithdrawalManager withdrawalManager,
                                   Clock clock) {
        this.withdrawalRepository = withdrawalRepository;
        this.payoutPort = payoutPort;
        this.withdrawalManager = withdrawalManager;
        this.clock = clock;
    }

    /**
     * 执行一次出款扫描（<b>不在事务里</b>：内部调渠道）。
     *
     * @param limit 扫描上限
     * @return 本次成功落账的单数
     */
    public int settle(int limit) {
        // now 只取一次：扫描上界与节流标记必须同一个值，否则边界处会抖动
        java.time.LocalDateTime now = java.time.LocalDateTime.ofInstant(clock.instant(), java.time.ZoneId.systemDefault());
        java.time.LocalDateTime cutBefore = now.minusMinutes(Math.max(1, retryIntervalMinutes));
        List<Long> payingIds = withdrawalRepository.findDuePayingIds(cutBefore, limit);
        int settled = 0;
        for (Long id : payingIds) {
            // 事务 A：先标记尝试（节流），0 行 = 状态已变 ⇒ 跳过。
            // 顺序有意为之：先标记后调渠道 ⇒ 崩了也不会立刻重打一次（反过来才会重试风暴）
            if (withdrawalRepository.markPayoutAttempt(id, now) == 0) {
                continue;
            }
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
        withdrawalRepository.markPayoutResult(withdrawal.getId(), summarize(result));
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
