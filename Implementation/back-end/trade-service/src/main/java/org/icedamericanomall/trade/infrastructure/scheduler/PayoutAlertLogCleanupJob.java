package org.icedamericanomall.trade.infrastructure.scheduler;

import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.trade.app.finance.PayoutAlertLogCleaner;
import org.springframework.stereotype.Component;

/**
 * 告警日志清理任务（XXL-Job 入口，薄壳）。
 *
 * <p>与出款、告警任务分开注册：清理失败不该让出款任务红，
 * 出款失败也不该把清理吞掉 —— 三件事的失败含义完全不同。
 *
 * <p>保留期口径在 {@link PayoutAlertLogCleaner}，本类只做入口。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PayoutAlertLogCleanupJob {

    private final PayoutAlertLogCleaner cleaner;

    @XxlJob("cleanupPayoutAlertLog")
    public void cleanupPayoutAlertLog() {
        long start = System.currentTimeMillis();
        int deleted = cleaner.purgeExpired();
        log.info("[payout] 告警日志清理任务完成 deleted={} costMs={}", deleted,
                System.currentTimeMillis() - start);
    }
}
