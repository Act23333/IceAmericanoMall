package org.icedamericanomall.trade.infrastructure.scheduler;

import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.trade.app.finance.WithdrawalPayoutService;
import org.springframework.stereotype.Component;

/**
 * 出款任务（XXL-Job 入口，薄壳）。
 *
 * <p>职责：把停在「付款中」的单推给渠道并落账。**查不到回执就保持「付款中」**，
 * 只由告警驱动人工介入 —— 这样「结果未知」和「确认失败」不会混成一个状态。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WithdrawalPayoutJob {

    private static final int DEFAULT_LIMIT = 100;

    private final WithdrawalPayoutService payoutService;

    @XxlJob("payoutPendingWithdrawals")
    public void payoutPendingWithdrawals() {
        long start = System.currentTimeMillis();
        int settled = payoutService.settle(DEFAULT_LIMIT);
        log.info("[payout] 出款任务完成 settled={} costMs={}", settled, System.currentTimeMillis() - start);
    }
}
