package org.icedamericanomall.pay.app;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.client.TradeInvoiceClient;
import org.icedamericanomall.common.enums.ErrorCategory;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.dto.InvoicePartialRefundCO;
import org.icedamericanomall.dto.InvoiceVoidCO;
import org.icedamericanomall.exception.UpstreamFailures;
import org.icedamericanomall.pay.domain.enums.InvoiceLinkKindEnum;
import org.icedamericanomall.pay.domain.model.invoicelink.InvoiceLinkCompensation;
import org.icedamericanomall.pay.domain.repository.InvoiceLinkCompensationRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 发票联动补偿重试编排（{@code invoice_link_compensation} 的<b>消费者</b>）——
 * 让上一批落的「可补偿」记录真正被收敛，而不是僵尸数据。
 *
 * <p><b>待补偿记录从哪来</b>：退款成功后的发票联动（I56 全额冲红 / I60 部分退款置标）是跨服务
 * Feign 调用，退款优先（PRD D4），联动失败只落一条 {@code PENDING} 记录（
 * {@code PayEventNotifier#triggerInvoiceLinkage} 的 {@code catch} 分支）。没有消费者时，那行记录躺在库里无人处理 ——
 * 本类就是它的消费者。
 *
 * <pre>
 * Scenario: 到期行重调成功
 *   Then 条件置 DONE（终态）并释放租约，不再被拾取
 * Scenario: 同一行被两个执行者同轮拾取（多实例 / XXL 与 @Scheduled 双入口）
 *   Then 只有认领命中（受影响行数 = 1）者重调；另一个跳过该行，记日志、不报错、不触发 Feign
 * Scenario: 持租约的进程崩在「Feign 成功、markDone 之前」
 *   Then 该行只是带着租约等待，租约到期后自动重回可认领集合（无需回收任务）
 * Scenario: 重调失败且可重试（TRANSIENT / BUG / 拿不到分类）
 *   Then retry_count + 1，next_retry_at = now + 2^n 分钟（封顶），last_error 落摘要，仍为 PENDING，释放租约
 * Scenario: 重调失败且次数耗尽（retry_count + 1 &gt;= MAX_RETRY）
 *   Then 条件置 FAILED_PERMANENT，释放租约，交管理端人工收敛
 * Scenario: 重调失败且下游 4xx（category == CLIENT：订单/发票已不存在、参数被拒）
 *   Then 直接 FAILED_PERMANENT，<b>不进退避</b>（重试必然重复失败）
 * Scenario: 某一单抛错（含状态回写失败）
 *   Then 记日志继续下一单，整批不中断
 * </pre>
 *
 * <p><b>事务纪律</b>：本类调 Feign，故<b>不加</b> {@code @Transactional}（硬约束）；状态迁移
 * 下沉为仓储条件更新，单条 UPDATE 自提交，重复/并发调度安全（谁先到谁生效）。
 *
 * <p><b>并发纪律：先认领再重调</b>（{@code tryClaim} + {@link #CLAIM_LEASE_SECONDS}）。任务有两个入口
 * （XXL-Job 与 {@code @Scheduled}）且多实例部署，同一次调度可能同时对同一行触发；不认领就重调，
 * 等于把「重复冲红」这件事寄托在「下游恰好幂等」这个<b>假设</b>上。认领把假设换成机制：
 * 只有命中「受影响行数 = 1」的执行者才允许重调，其余跳过该行（记 info，不算错误）。
 *
 * <p><b>租约 = 会自愈的时间盒，不是一个状态</b>：{@code lease_until} 到期后该行自动回到可认领集合
 * （到期拾取查询本身就带租约条件），所以<b>不需要回收任务</b>，也不需要引入 {@code PROCESSING} 状态 ——
 * 后者只会换出一个「进程崩在处理中就永久卡住、还得再写一个回收任务去救」的新僵尸态。
 * 进程在「Feign 调用成功、{@code markDone} 尚未执行」之间崩溃时，该行只是带着租约等它过期，
 * 之后被下一轮重新拾取（下游幂等使重复重调无害）。
 *
 * <p><b>重放参数的来源</b>：只用 {@code order_no} + {@code scene_ref} + {@code kind} 三列还原调用，
 * 与 {@code PayEventNotifier#triggerInvoiceLinkage} 的入参口径逐字一致（{@code scene_ref}
 * 就是 {@code refund_order.biz_ref} 的复制），故重调与首发对下游完全等价。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InvoiceLinkCompensationRetryManager {

    /** 单轮最大处理条数 —— 取值与依据见 {@link InvoiceLinkCompensationLeasePolicy}。 */
    static final int BATCH_LIMIT = InvoiceLinkCompensationLeasePolicy.BATCH_LIMIT;

    /**
     * 认领租约时长（秒）—— 取值、以及「为什么必须按最坏批耗时而不是平均耗时估算」，
     * 全部见 {@link InvoiceLinkCompensationLeasePolicy}（那里还写明了时钟口径与「租约 ≠ 分布式锁」的边界）。
     */
    static final int CLAIM_LEASE_SECONDS = InvoiceLinkCompensationLeasePolicy.CLAIM_LEASE_SECONDS;

    /**
     * 最大重试次数：{@code retry_count + 1 >= MAX_RETRY} 即转 {@code FAILED_PERMANENT}。
     * 取值理由：退避是 2^n 分钟，5 次退避的累计窗口约 2+4+8+16 = 30 分钟 —— 覆盖下游分钟级抖动；
     * 再往后失败的几乎都是确定性缺陷（数据缺陷/契约变更），继续重试只会拖长「可观测但无进展」的窗口。
     */
    static final int MAX_RETRY = 5;

    /** 退避封顶（分钟）：{@code 2^n} 无上限会算出「几天后再试」，等于事实停摆。 */
    static final int MAX_BACKOFF_MINUTES = 60;

    /** 退避指数上限：{@code 2^6 = 64} 已超过封顶值，再大没有意义，同时避免移位溢出。 */
    private static final int BACKOFF_EXPONENT_CAP = 6;

    private final InvoiceLinkCompensationRepository invoiceLinkCompensationRepository;
    private final TradeInvoiceClient tradeInvoiceClient;

    /**
     * 补偿一轮：拾取到期记录 → <b>逐条先认领</b> → 认领成功才重调发票联动 → 按结果推进状态。
     *
     * <p>单条异常（含状态回写失败）仅记日志、继续下一单：该行仍是 {@code PENDING}
     * （或已由他人推进），租约到期后自然再次被拾取；下游联动本身幂等（无有效票是空操作），
     * 不会因重复重调而重复冲红。
     *
     * @return 本轮拾取的到期记录数（供调度侧观测；其中被他人认领而跳过的条数见 claim 日志）
     */
    public int retryDueCompensations() {
        List<InvoiceLinkCompensation> due = invoiceLinkCompensationRepository.findDueForRetry(BATCH_LIMIT);
        log.info("[refund-invoice][retry][scan] limit={}, dueCount={}", BATCH_LIMIT, due.size());
        int claimed = 0;
        int skipped = 0;
        for (InvoiceLinkCompensation compensation : due) {
            try {
                if (retryOneIfClaimed(compensation)) {
                    claimed++;
                } else {
                    skipped++;
                }
            } catch (RuntimeException e) {
                log.error("[refund-invoice][retry][aborted] id={} 单笔补偿意外异常，继续下一笔",
                        compensation.getId(), e);
            }
        }
        log.info("[refund-invoice][retry][claimed] dueCount={}, claimed={}, skipped={}",
                due.size(), claimed, skipped);
        return due.size();
    }

    /**
     * 认领 + 重试（认领失败即跳过，<b>不触碰 Feign</b>）。
     *
     * <p>认领与重调必须成对出现在这里，不允许调用方绕过：只要有一条路径「不认领就重调」，
     * 并发互斥就整体失效（多实例下同一行被重复冲红）。认领未命中不是错误 ——
     * 说明别的执行者已经在处理，或该行已被推进到终态。
     *
     * <p>认领成功后若本进程崩溃，该行只是带着租约等它到期再被拾取；不会永久卡住（无回收任务，
     * 因为租约过期本身即回收）。
     *
     * @param compensation 到期补偿记录
     * @return {@code true} 本轮由本执行者认领并重试；{@code false} 认领未命中（跳过）
     */
    private boolean retryOneIfClaimed(InvoiceLinkCompensation compensation) {
        if (!invoiceLinkCompensationRepository.tryClaim(compensation.getId(), CLAIM_LEASE_SECONDS)) {
            log.info("[refund-invoice][retry][claim-skip] id={}, orderNo={}, sceneRef={} 认领未命中"
                            + "（他人持有租约或状态已推进），本轮跳过（非错误）",
                    compensation.getId(), compensation.getOrderNo(), compensation.getSceneRef());
            return false;
        }
        retryOne(compensation);
        return true;
    }

    /**
     * 单笔重试：先重调 Feign，成功才条件置 {@code DONE}；失败按类别分派（退避 / 永久失败）。
     *
     * <p>{@code markDone} 刻意在 {@code try} 之外：只有「重调 Feign 失败」才谈补偿策略，
     * 状态回写失败属于持久层故障（该行仍是 {@code PENDING}，租约到期后被下一轮再拾取），
     * 直接抛给批量循环兜底记录，不伪装成「联动失败」。
     *
     * @param compensation 到期补偿记录（含 {@code id}，来自 {@code findDueForRetry}）
     */
    void retryOne(InvoiceLinkCompensation compensation) {
        try {
            redispatch(compensation);
        } catch (RuntimeException e) {
            onRetryFailure(compensation, e);
            return;
        }
        boolean marked = invoiceLinkCompensationRepository.markDone(compensation.getId());
        log.info("[refund-invoice][retry][done] id={}, orderNo={}, sceneRef={}, kind={}, marked={}",
                compensation.getId(), compensation.getOrderNo(), compensation.getSceneRef(),
                compensation.getKind(), marked);
    }

    /**
     * 按 {@code kind} 重调对应的发票联动接口（两条分支的补偿语义不可互换，见 {@link InvoiceLinkKindEnum}）。
     *
     * @param compensation 补偿记录；{@code orderNo} 已是 32 位 hex 线格式（仓储侧经
     *                     {@code IdGenerator#toWire} 还原），直接作为对外业务键传给 trade
     */
    private void redispatch(InvoiceLinkCompensation compensation) {
        switch (InvoiceLinkKindEnum.of(compensation.getKind())) {
            case VOID -> tradeInvoiceClient.voidOnFullRefund(
                    new InvoiceVoidCO(compensation.getOrderNo(), compensation.getSceneRef()));
            case PARTIAL -> tradeInvoiceClient.markPartialRefund(
                    new InvoicePartialRefundCO(compensation.getOrderNo(), compensation.getSceneRef()));
        }
    }

    /**
     * 失败分派：<b>下游 4xx 短路为永久失败</b>；其余（可重试 / 次数耗尽）走退避或终态。
     *
     * <p><b>为什么 CLIENT 直接终态</b>：{@code ErrorCategory#CLIENT} = 下游明确说「你请求的这条数据
     * 不存在 / 参数被拒」（订单已删除、发票记录缺失、契约字段变了）。这类失败与时间无关，
     * 退避重试只会把同一枚确定性错误重复 5 次、把「已确定要人工处理」推后半小时。
     *
     * <p>其余类别一律走退避（含 {@code BUG} 与「拿不到分类」的未知）：
     * {@code 09-constraints} 的未知口径是「未知按可重试处理，绝不当成功」，
     * 且 500 类缺陷有可能在下一轮部署后自愈。
     *
     * @param compensation 补偿记录
     * @param cause        重调失败原因
     */
    private void onRetryFailure(InvoiceLinkCompensation compensation, RuntimeException cause) {
        ErrorCategory category = UpstreamFailures.classify(cause);
        int attempted = retryCountOf(compensation) + 1;
        String lastError = describe(cause, category);
        if (category == ErrorCategory.CLIENT) {
            boolean marked = invoiceLinkCompensationRepository.markFailedPermanent(
                    compensation.getId(), lastError);
            log.warn("[refund-invoice][retry][permanent] id={}, orderNo={}, sceneRef={} 下游拒绝"
                            + "(category=CLIENT，重试必然重复失败)，不进退避直接置永久失败: marked={}, lastError={}",
                    compensation.getId(), compensation.getOrderNo(), compensation.getSceneRef(),
                    marked, lastError);
            return;
        }
        if (attempted >= MAX_RETRY) {
            boolean marked = invoiceLinkCompensationRepository.markFailedPermanent(
                    compensation.getId(), lastError);
            log.error("[refund-invoice][retry][exhausted] id={}, orderNo={}, sceneRef={} 重试次数耗尽"
                            + "({}/{}，category={})，置永久失败待人工收敛: marked={}",
                    compensation.getId(), compensation.getOrderNo(), compensation.getSceneRef(),
                    attempted, MAX_RETRY, category, marked);
            return;
        }
        LocalDateTime nextRetryAt = LocalDateTime.now().plusMinutes(backoffMinutes(attempted));
        boolean scheduled = invoiceLinkCompensationRepository.scheduleRetry(
                compensation.getId(), attempted, nextRetryAt, lastError);
        log.warn("[refund-invoice][retry][scheduled] id={}, orderNo={}, sceneRef={} 联动仍失败"
                        + "(category={})，第 {} 次重试后退避至 {}: scheduled={}, lastError={}",
                compensation.getId(), compensation.getOrderNo(), compensation.getSceneRef(), category,
                attempted, nextRetryAt, scheduled, lastError);
    }

    /**
     * 退避时长（分钟）：{@code 2^retryCount}，封顶 {@link #MAX_BACKOFF_MINUTES}。
     *
     * <p>指数退避而非固定间隔：下游故障（trade 不可用 / 超时 / 熔断）通常分钟级恢复，
     * 固定 1 分钟重试会在故障窗口内制造 5 倍无效流量，而指数退避让前几轮快速命中恢复时刻、
     * 后几轮自动降温，把「重试预算」花在真实恢复窗口上。
     *
     * @param retryCount 本次失败后的累计重试次数（≥ 1）
     * @return 退避分钟数（1 ≤ 值 ≤ {@link #MAX_BACKOFF_MINUTES}）
     */
    static long backoffMinutes(int retryCount) {
        int exponent = Math.min(Math.max(retryCount, 0), BACKOFF_EXPONENT_CAP);
        return Math.min(1L << exponent, MAX_BACKOFF_MINUTES);
    }

    /** 已重试次数（{@code null} 按 0 处理：历史/脏数据的防御口径）。 */
    private static int retryCountOf(InvoiceLinkCompensation compensation) {
        return compensation.getRetryCount() == null ? 0 : compensation.getRetryCount();
    }

    /**
     * 失败摘要（落 {@code last_error}，超长由仓储按列宽截断）—— 带<b>业务错误码与处置类别</b>，
     * 让运维在补偿表里就能区分「超时（TRANSIENT，会自愈）」与「下游数据不存在（CLIENT，要人工）」，
     * 不必再翻日志对齐时间线。口径与 {@code PayEventNotifier#describe} 一致，只多一个类别标记。
     */
    private static String describe(RuntimeException cause, ErrorCategory category) {
        String prefix = cause.getClass().getSimpleName();
        if (cause instanceof BizException bizException && bizException.getErrorCode() != null) {
            prefix = prefix + "[" + bizException.getErrorCode().name() + "]";
        }
        prefix = prefix + " category=" + category.name();
        return cause.getMessage() == null ? prefix : prefix + ": " + cause.getMessage();
    }
}
