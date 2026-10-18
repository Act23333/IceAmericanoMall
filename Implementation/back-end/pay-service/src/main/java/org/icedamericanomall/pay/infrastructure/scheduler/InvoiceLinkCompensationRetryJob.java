package org.icedamericanomall.pay.infrastructure.scheduler;

import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.pay.app.InvoiceLinkCompensationRetryManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 发票联动补偿重试任务 —— {@code invoice_link_compensation}（PENDING）的定时消费者。
 *
 * <p><b>为什么需要</b>：退款成功的发票联动（I56 全额冲红 / I60 部分置标）失败后只落一条
 * {@code PENDING} 记录，<b>退款优先</b>（PRD D4）不变。若无人消费，这张表就是僵尸数据 ——
 * 退款已到买家账上，发票却仍是有效状态，且无人知道丢了一次联动。本任务把「可补偿」变成
 * 「已收敛（DONE）」或「需人工（FAILED_PERMANENT）」。
 *
 * <p><b>调度形态沿用同包既有 Job</b>（{@link RefundResumeJob} / {@link ReconciliationJob} /
 * {@link PayTimeoutJob}）：{@code @Component} + {@code @Slf4j} + {@code @RequiredArgsConstructor}，
 * 编排下沉到 app 层 Manager（{@link InvoiceLinkCompensationRetryManager}），本类只做触发与日志；
 * 生产入口为 {@code @XxlJob}（dev 环境 {@code xxl.job.enabled=false}，{@code ia-common XxlJobConfig}
 * 不注册执行器，Handler 不会自动运行）。
 *
 * <p><b>与既有 Job 的唯一差异（本批新增的运维开关，不改既有类）</b>：
 * <ul>
 *   <li>类级 {@code @ConditionalOnProperty(pay.invoice.compensation.retry.enabled)}，默认 {@code true}
 *       （= 既有 Job 的「默认生效」行为，只是可关）；测试/演练设 {@code false} 即整个 Bean 不注册，
 *       两种入口都不会跑；</li>
 *   <li>{@code @Scheduled} 开发/单机入口，间隔由 {@code pay.invoice.compensation.retry.interval-ms}
 *       控制（默认 60000）：本任务与既有 Job 不同 —— 它是「补偿积压能否在合理时间内收敛」的
 *       直接决定项，因此除了 XXL-Job Admin 的 cron，还需要一个进程内可配置节奏的兜底；
 *       生产若只用 XXL-Job Admin 调度，把 {@code pay.invoice.compensation.retry.scheduled-enabled=false}
 *       关掉进程内入口即可（默认 {@code true}，因为 dev 环境没有 XXL 时可自驱）。</li>
 * </ul>
 *
 * <p><b>并发限制（如实照既有口径，不自创一套）</b>：同包三个既有 Job <b>都没有</b>分布式锁
 * （{@code RefundResumeJob} / {@code ReconciliationJob} / {@code PayTimeoutJob} 一律裸触发），
 * 本 Job 同样不加锁 —— 但补偿这一类<b>不允许</b>靠「下游幂等」兜底重复重调，故并发裁决下沉到数据层，
 * 由三层机制共同保证「至多一次有效推进」：
 * <ol>
 *   <li><b>认领租约</b>（{@code lease_until}）：拾取后先 {@code tryClaim}，只有命中「受影响行数 = 1」
 *       的执行者才重调，其余跳过该行（{@code InvoiceLinkCompensationRetryManager#CLAIM_LEASE_SECONDS}）；</li>
 *   <li><b>状态迁移全是条件更新</b>（{@code WHERE id = ? AND status = PENDING}）—— 同一行被两个执行者
 *       拾取时，只有一个能推进状态，另一个的 {@code markDone / scheduleRetry} 命中 0 行、记日志跳过，
 *       不会把终态行拉回待补偿，也不会覆盖彼此的 {@code retry_count} / {@code next_retry_at}；</li>
 *   <li><b>下游联动本身幂等</b>（trade 侧：无有效票 = 空操作；同一订单的冲红/置标重复调用结果一致）——
 *       这一层现在只是<b>纵深防御</b>，不再是重复重调的主要防线。</li>
 * </ol>
 *
 * <p><b>剩余风险（明确记在此处而非掩盖）</b>：租约是<b>时间盒</b>而非分布式锁 ——
 * 若某次 Feign 调用耗时超过租约时长（默认 120s，远大于单批估算耗时 20s），该行可能在原持有者
 * 仍在调用时被另一执行者再次认领，形成一次并发重调；此时由第 3 层（下游幂等）兜住。
 * 租约到期即自动回收，故<b>崩溃后不需要人工干预、也不需要回收任务</b>（这是选租约而不是
 * {@code PROCESSING} 状态的全部理由：新状态会换出一个会永久卡住的僵尸态）。
 * 要彻底消除上面的窗口，需引入 {@code DistributedLock}（ADR-024）或 XXL-Job 的分片/串行策略
 * —— 那属跨 Job 的统一决策，不在本批单独为一张补偿表另起一套。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "pay.invoice.compensation.retry", name = "enabled", matchIfMissing = true)
public class InvoiceLinkCompensationRetryJob {

    private final InvoiceLinkCompensationRetryManager retryManager;

    /** 进程内定时入口开关（默认开：dev 环境没有 XXL-Job 时靠它驱动；生产只用 XXL 时置 false）。 */
    private final boolean scheduledEnabled;

    /**
     * 构造器注入（无字段注入）：{@code scheduledEnabled} 由配置解析，仅供进程内入口自检。
     *
     * @param retryManager     app 层重试编排
     * @param scheduledEnabled 进程内 {@code @Scheduled} 入口是否生效
     */
    public InvoiceLinkCompensationRetryJob(
            InvoiceLinkCompensationRetryManager retryManager,
            @Value("${pay.invoice.compensation.retry.scheduled-enabled:true}") boolean scheduledEnabled) {
        this.retryManager = retryManager;
        this.scheduledEnabled = scheduledEnabled;
    }

    /**
     * 补偿一轮（生产入口，XXL-Job Admin 调度）。
     *
     * <p>断点/重复调度安全：认领租约 + 状态迁移条件化 + 下游幂等（见类注释）。
     */
    @XxlJob("retryInvoiceLinkCompensation")
    public void retryInvoiceLinkCompensation() {
        log.info("XXL-Job: retryInvoiceLinkCompensation triggered");
        int scanned = retryManager.retryDueCompensations();
        log.info("XXL-Job: retryInvoiceLinkCompensation done, scanned={}", scanned);
    }

    /**
     * 补偿一轮（开发/单机入口，间隔 = {@code pay.invoice.compensation.retry.interval-ms}，默认 60s）。
     *
     * <p>与 {@link #retryInvoiceLinkCompensation()} 调同一个 Manager 方法，语义完全等价 ——
     * 两个入口并存只为「有 XXL 用 XXL，没有能自驱」，不是两种行为。
     */
    @Scheduled(fixedDelayString = "${pay.invoice.compensation.retry.interval-ms:60000}")
    public void retryInvoiceLinkCompensationScheduled() {
        if (!scheduledEnabled) {
            return;
        }
        log.info("Scheduled: retryInvoiceLinkCompensation triggered");
        int scanned = retryManager.retryDueCompensations();
        log.info("Scheduled: retryInvoiceLinkCompensation done, scanned={}", scanned);
    }
}
