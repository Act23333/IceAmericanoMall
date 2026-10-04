package org.icedamericanomall.pay.infrastructure.scheduler;

import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.pay.app.ReconciliationManager;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 渠道对账日终任务（F4）—— 每日对「昨日」账单执行全渠道对账。
 *
 * <p><b>调度口径</b>：与 {@code PayTimeoutJob} / trade {@code AbnormalOrderJob} 一致 ——
 * 由 XXL-Job 触发；开发环境 {@code xxl.job.enabled=false}（{@code ia-common XxlJobConfig}
 * 不注册执行器），因此本 Handler 在 dev 不会自动运行，需手动触发管理端 AD46 或打开调度开关。
 * 断点重跑安全：同一账单日重跑走「先删后写」（{@code uk_date_channel} 兜底）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReconciliationJob {

    private final ReconciliationManager reconciliationManager;

    /** 日终对账（账单日 = 昨日）。 */
    @XxlJob("runDailyReconciliation")
    public void runDailyReconciliation() {
        LocalDate billDate = LocalDate.now().minusDays(1);
        log.info("XXL-Job: runDailyReconciliation triggered, billDate={}", billDate);
        reconciliationManager.run(billDate, null);
    }
}
