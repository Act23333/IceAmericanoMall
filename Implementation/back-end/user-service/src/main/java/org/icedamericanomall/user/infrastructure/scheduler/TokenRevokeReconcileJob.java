package org.icedamericanomall.user.infrastructure.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.user.app.user.TokenRevokeReconcileManager;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 令牌吊销对账补偿的<b>裸触发入口</b>（U17 第二步）—— 编排全部在
 * {@link TokenRevokeReconcileManager}，本类只负责定时与异常兜底。
 *
 * <p><b>为什么用 {@code @Scheduled} 而不是 {@code @XxlJob}</b>：{@code xxl-job-core} 在
 * {@code ia-common} 里是 {@code optional}，user-service 并未引入它，也没有 XXL 执行器配置；
 * 为单个补偿任务新引一个调度框架不值得。生产若需集中调度，改 {@code @XxlJob} 即可
 * （pay-service 的四个 Job 就是这么两种入口并存的）。
 *
 * <p><b>间隔</b>：{@code user.token-revoke-reconcile.interval-ms}，默认 15 分钟。补偿窗口
 * （{@link TokenRevokeReconcileManager#LOOKBACK_HOURS} 小时）远大于间隔，故单次失败不会漏。
 *
 * <p><b>异常不外抛</b>：调度线程里抛出的异常会被 Spring 记一条日志后静默吞掉（下次照跑），
 * 但那会让「本轮失败」与「本轮没活干」在日志里难以区分，故这里显式接住并打 ERROR。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TokenRevokeReconcileJob {

    private final TokenRevokeReconcileManager tokenRevokeReconcileManager;

    /** 跑一轮对账（幂等；重复调度安全，见 Manager 的并发说明）。 */
    @Scheduled(fixedDelayString = "${user.token-revoke-reconcile.interval-ms:900000}")
    public void reconcileTokenRevoke() {
        try {
            int replayed = tokenRevokeReconcileManager.reconcile();
            if (replayed > 0) {
                log.info("[token-revoke][job] 本轮重放成功 {} 条", replayed);
            }
        } catch (Exception e) {
            log.error("[token-revoke][job] 本轮对账失败（下一轮继续，窗口内不会漏）", e);
        }
    }
}
