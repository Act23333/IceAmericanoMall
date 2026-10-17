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
 *   Then 条件置 DONE（终态），不再被拾取
 * Scenario: 重调失败且可重试（TRANSIENT / BUG / 拿不到分类）
 *   Then retry_count + 1，next_retry_at = now + 2^n 分钟（封顶），last_error 落摘要，仍为 PENDING
 * Scenario: 重调失败且次数耗尽（retry_count + 1 &gt;= MAX_RETRY）
 *   Then 条件置 FAILED_PERMANENT，交管理端人工收敛
 * Scenario: 重调失败且下游 4xx（category == CLIENT：订单/发票已不存在、参数被拒）
 *   Then 直接 FAILED_PERMANENT，<b>不进退避</b>（重试必然重复失败）
 * Scenario: 某一单抛错（含状态回写失败）
 *   Then 记日志继续下一单，整批不中断
 * </pre>
 *
 * <p><b>事务纪律</b>：本类调 Feign，故<b>不加</b> {@code @Transactional}（硬约束）；状态迁移
 * 下沉为仓储条件更新，单条 UPDATE 自提交，重复/并发调度安全（谁先到谁生效）。
 *
 * <p><b>重放参数的来源</b>：只用 {@code order_no} + {@code scene_ref} + {@code kind} 三列还原调用，
 * 与 {@code PayEventNotifier#triggerInvoiceLinkage} 的入参口径逐字一致（{@code scene_ref}
 * 就是 {@code refund_order.biz_ref} 的复制），故重调与首发对下游完全等价。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InvoiceLinkCompensationRetryManager {

    /**
     * 单轮最大处理条数：限制单轮 Feign 调用总耗时（按单笔 ~200ms 估算约 20s），远低于分钟级
     * 调度间隔，避免任务重叠堆积；积压靠多轮滚动消化（到期时间升序，老单优先收敛）。
     * 与 {@code RefundResumeManager#BATCH_LIMIT} 同口径。
     */
    static final int BATCH_LIMIT = 100;

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
     * 补偿一轮：拾取到期记录 → 逐条重调发票联动 → 按结果推进状态。
     *
     * <p>单条异常（含状态回写失败）仅记日志、继续下一单：该行仍是 {@code PENDING}
     * （或已由他人推进），下轮自然再次拾取；下游联动本身幂等（无有效票是空操作），
     * 不会因重复重调而重复冲红。
     *
     * @return 本轮拾取的到期记录数（供调度侧观测）
     */
    public int retryDueCompensations() {
        List<InvoiceLinkCompensation> due = invoiceLinkCompensationRepository.findDueForRetry(BATCH_LIMIT);
        log.info("[refund-invoice][retry][scan] limit={}, dueCount={}", BATCH_LIMIT, due.size());
        for (InvoiceLinkCompensation compensation : due) {
            try {
                retryOne(compensation);
            } catch (RuntimeException e) {
                log.error("[refund-invoice][retry][aborted] id={} 单笔补偿意外异常，继续下一笔",
                        compensation.getId(), e);
            }
        }
        return due.size();
    }

    /**
     * 单笔重试：先重调 Feign，成功才条件置 {@code DONE}；失败按类别分派（退避 / 永久失败）。
     *
     * <p>{@code markDone} 刻意在 {@code try} 之外：只有「重调 Feign 失败」才谈补偿策略，
     * 状态回写失败属于持久层故障（该行仍是 {@code PENDING}，下轮会再拾取），
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
