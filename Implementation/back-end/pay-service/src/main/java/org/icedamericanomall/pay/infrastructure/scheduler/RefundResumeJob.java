package org.icedamericanomall.pay.infrastructure.scheduler;

import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.pay.app.RefundResumeManager;
import org.springframework.stereotype.Component;

/**
 * 悬挂退款补偿任务（ADR-035「后果/负面风险」第 3 项收敛）—— 主动收敛「处理中」超时未终态的退款单。
 *
 * <p><b>为什么需要</b>：微信渠道退款受理后 settled=false，终态只靠 PAY6 回调推进；回调丢失/未达时
 * 退款单永久停留处理中，而管理端 AD43 只允许「失败」单重试，处理中单没有任何收敛路径。
 * 本任务与 PAY6 回调互为冗余（条件更新保证并发下只有一个生效），与 AD43 互补
 * （补偿置失败的单一经管理端重试收敛）。
 *
 * <p><b>调度口径</b>：与 {@link ReconciliationJob} / {@code PayTimeoutJob} / trade
 * {@code AbnormalOrderJob} 一致 —— 由 XXL-Job 触发；开发环境 {@code xxl.job.enabled=false}
 * （{@code ia-common XxlJobConfig} 不注册执行器），因此本 Handler 在 dev 不会自动运行。
 * 断点/重复调度安全：状态迁移均为条件更新（仅处理中可迁移），余额/支付宝重放按
 * {@code refundNo} 幂等，不会重复出款或重复回写售后单。悬挂阈值与单轮批量见
 * {@link RefundResumeManager} 常量说明。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RefundResumeJob {

    private final RefundResumeManager refundResumeManager;

    /** 悬挂退款补偿一轮（扫描 → 逐单按渠道收敛）。 */
    @XxlJob("resumeStuckRefunds")
    public void resumeStuckRefunds() {
        log.info("XXL-Job: resumeStuckRefunds triggered");
        int scanned = refundResumeManager.resumeStuckRefunds();
        log.info("XXL-Job: resumeStuckRefunds done, scanned={}", scanned);
    }
}
