package org.icedamericanomall.trade.app.finance;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.trade.domain.repository.WithdrawalRepository;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * 告警日志保留期清理（应用层用例）。
 *
 * <p><b>为什么不清理单据上的尝试痕迹</b>：`last_payout_attempt_at` / `last_payout_result` 是
 * 「什么时候试过、结果是什么」的审计事实，出款争议时要靠它回溯 —— 它们随单据生命周期存在，
 * **不是**日志，不做保留期删除。（曾把它登记成待清理项，属于登记错误，已撤销。）
 *
 * <p><b>保留期固定在这个类里</b>：不放进配置 —— 保留期是合规口径，不是可调参数；
 * 要改就改代码、走评审，而不是让某个环境的配置悄悄把审计证据删早。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PayoutAlertLogCleaner {

    /** 告警日志保留天数（超过即删）。 */
    public static final int RETENTION_DAYS = 180;

    private final WithdrawalRepository withdrawalRepository;

    /** 生产用系统时钟；测试可注入固定时钟。 */
    public int purgeExpired() {
        return purgeExpired(Clock.systemUTC());
    }

    /**
     * 清理早于保留期的告警日志。
     *
     * @param clock 时钟（测试注入固定时钟，避免用例随真实时间漂移）
     * @return 删除行数
     */
    public int purgeExpired(Clock clock) {
        LocalDateTime cutoff = cutoffFor(clock);
        int deleted = withdrawalRepository.purgeAlertLogBefore(cutoff);
        log.info("[payout] 告警日志清理完成 cutoff={} deleted={}", cutoff, deleted);
        return deleted;
    }

    /** 保留期边界：早于它的一律删除。 */
    public static LocalDateTime cutoffFor(Clock clock) {
        return LocalDateTime.now(clock.withZone(ZoneOffset.UTC)).minusDays(RETENTION_DAYS);
    }
}
