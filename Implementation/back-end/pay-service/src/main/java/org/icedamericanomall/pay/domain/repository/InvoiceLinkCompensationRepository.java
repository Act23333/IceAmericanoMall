package org.icedamericanomall.pay.domain.repository;

import org.icedamericanomall.pay.domain.model.invoicelink.InvoiceLinkCompensation;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 发票联动补偿仓储（领域端口）—— 实现方：{@code infrastructure.persistence.InvoiceLinkCompensationRepositoryImpl}。
 *
 * <p><b>幂等锚点</b>：{@code (order_no, scene_ref, kind)}（{@code uk_order_scene_kind}）。
 * 同一退款场景的同一类联动重复失败只留一条记录；重复写入<b>不是错误</b>，
 * 由数据库唯一键兜底并在此归一为「幂等命中」。
 *
 * <p><b>状态迁移一律条件更新</b>（{@code WHERE id = ? AND status = 期望态}），由实现方（RepositoryImpl
 * + Mapper 显式 SQL）承担 —— 与 {@code RefundOrderRepository#markSuccessIfProcessing} 同口径：
 * 调度器可能被重复/并发触发（见 {@code InvoiceLinkCompensationRetryJob} 类注释），
 * 「读-改-写」会让两个执行者都以为自己推进了状态，从而重复补偿、覆盖彼此的
 * {@code retry_count} / {@code next_retry_at}。条件更新把并发裁决交给数据库，命中行数即成败。
 */
public interface InvoiceLinkCompensationRepository {

    /**
     * 新增「待补偿」记录（幂等）。
     *
     * <p><b>不抛 {@code DuplicateKeyException}</b>：撞唯一键 = 该补偿已存在，返回 {@code false} 即可。
     * 其余持久化异常（连接不可用等）照常向上抛 —— 是否吞掉由调用方决定（退款主流程会吞并告警）。
     *
     * @param compensation 待补偿记录（由 {@link InvoiceLinkCompensation#newPending} 装配，{@code id} 忽略）
     * @return {@code true} 新建成功；{@code false} 幂等命中（同 {@code (order_no, scene_ref, kind)} 已存在）
     */
    boolean createPending(InvoiceLinkCompensation compensation);

    /**
     * 拾取<b>到期</b>的待补偿记录（补偿重试任务的输入）。
     *
     * <p>筛选口径：{@code status = PENDING(0) AND next_retry_at <= now()}，按 {@code next_retry_at}
     * 升序（老的先收敛），最多 {@code limit} 条 —— <b>限量是硬要求</b>：单轮条数无界时，
     * 一次积压峰值就能把一轮任务的耗时拉到远超调度间隔，进而重叠堆积。命中索引
     * {@code idx_status_next_retry(status, next_retry_at)}。
     *
     * @param limit 单轮最大条数（由调用方常量给出，不接受外部输入）
     * @return 到期记录列表（可能为空，绝不为 {@code null}）
     */
    List<InvoiceLinkCompensation> findDueForRetry(int limit);

    /**
     * 置「已补偿」（{@code PENDING → DONE}，条件更新）。
     *
     * <p><b>幂等语义由条件保证</b>：仅当该行仍是 {@code PENDING} 时命中。重复调用第二次返回
     * {@code false} —— 并发调度下「谁先到谁生效」，后到者不覆盖终态。
     *
     * @param id 补偿记录技术主键（调度器内部口径）
     * @return {@code true} 本次推进成功；{@code false} 该行已不在 {@code PENDING}（他人已推进或已终态）
     */
    boolean markDone(Long id);

    /**
     * 安排下一次重试（{@code PENDING} 下写 {@code retry_count / next_retry_at / last_error}，条件更新）。
     *
     * <p>仅在 {@code PENDING} 时生效：若该行已被其它执行者推进到 {@code DONE} /
     * {@code FAILED_PERMANENT}，本次退避写入不生效（返回 {@code false}），不会把终态行拉回待补偿。
     *
     * @param id          补偿记录技术主键
     * @param retryCount  已重试次数（本次失败后的累计值，非增量）
     * @param nextRetryAt 下次可拾取时间（退避：{@code 2^n} 分钟，封顶见调度侧常量）
     * @param lastError   本次失败摘要（实现方按列宽 {@link InvoiceLinkCompensation#LAST_ERROR_MAX_LENGTH} 截断）
     * @return {@code true} 退避已写入；{@code false} 该行已不在 {@code PENDING}
     */
    boolean scheduleRetry(Long id, int retryCount, LocalDateTime nextRetryAt, String lastError);

    /**
     * 置「永久失败」（{@code PENDING → FAILED_PERMANENT}，条件更新）—— 终态，交管理端人工收敛。
     *
     * <p>两条触发路径：重试次数耗尽；<b>下游 4xx</b>（{@code ErrorCategory#CLIENT}：订单/发票已不存在、
     * 参数被拒 —— 重试必然重复失败）。
     *
     * @param id        补偿记录技术主键
     * @param lastError 终态原因摘要（实现方按列宽截断）
     * @return {@code true} 本次推进成功；{@code false} 该行已不在 {@code PENDING}
     */
    boolean markFailedPermanent(Long id, String lastError);
}
