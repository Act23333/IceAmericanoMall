package org.icedamericanomall.marketing.infrastructure.scheduler;

import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.marketing.app.groupbuy.GroupBuyParticipationManager;
import org.springframework.stereotype.Component;

/**
 * 拼团结团日终任务（F13，V5.14，ADR-049）—— 扫描超时未满员的拼团中团并联动退款。
 *
 * <p><b>调度口径</b>：与 pay {@code ReconciliationJob} / trade {@code AbnormalOrderJob} 一致 ——
 * 由 XXL-Job 触发；开发环境 {@code xxl.job.enabled=false}（{@code ia-common XxlJobConfig}
 * 不注册执行器），因此本 Handler 在 dev 不会自动运行，需打开调度开关或手动触发。
 * 执行语义幂等安全：条件置失败（1→3）保证并发/重跑下仅一个执行方触发退款联动；
 * 单团失败 try/catch 继续下一团（应用层 {@code expireOverdueTeams}）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GroupBuyExpireJob {

    private final GroupBuyParticipationManager groupBuyParticipationManager;

    /** 结团扫描（团状态机 1→3 + I54 逐单退款触发，fail-open）。 */
    @XxlJob("expireGroupTeams")
    public void expireGroupTeams() {
        log.info("XXL-Job: expireGroupTeams triggered");
        groupBuyParticipationManager.expireOverdueTeams();
    }
}
