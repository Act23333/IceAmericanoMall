package org.icedamericanomall.pay.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 发票联动补偿 Mapper —— 入参/出参均为 PO（本服务实体即 PO）。
 *
 * <p><b>除 BaseMapper 标准方法外，五条显式 SQL</b>：补偿重试任务的读侧筛选口径
 * （{@code status + next_retry_at + 租约 + 限量}）、一条认领（{@code lease_until} 时间盒）与三条状态迁移
 * （{@code PENDING → DONE / 退避 / → 永久失败}）都是<b>契约级不变式</b>，收口在 Mapper 层显式可审计 ——
 * 与 {@link RefundOrderMapper#selectStaleProcessing} 同口径（筛选口径不在 Wrapper 里拼），
 * 与 trade {@code AfterSaleMapper.updateStatusConditionally} 同理。
 *
 * <p><b>为什么状态迁移必须写在 SQL 的 {@code WHERE} 里</b>：调度器可能被重复/并发触发
 * （同包既有 Job 均未加分布式锁，见 {@code InvoiceLinkCompensationRetryJob} 类注释）。
 * 「先查后写」的读-改-写在并发下会双写成功，导致同一补偿被补偿两次、或终态被退避回
 * {@code PENDING}。条件更新让数据库做唯一裁决：命中行数 &gt; 0 才算本次推进生效。
 *
 * <p><b>为什么用 {@code lease_until} 而不是新增一个 {@code PROCESSING} 状态</b>：状态值是业务语义
 * （PENDING / DONE / FAILED_PERMANENT），多一个「正在处理」态就多一个<b>可能永久卡住</b>的僵尸态，
 * 于是必须再写一个回收任务去救它（并覆盖「进程崩在回收之前」的窗口）。{@code lease_until} 是<b>会自愈的
 * 时间盒</b>：到期即自动回到可认领集合（见 {@link #claimIfLeasable}），不需要任何回收任务，
 * 也不需要为「认领中的行」新增状态值。业务语义与并发控制由此解耦。
 *
 * <p><b>状态码一律由调用方以参数传入</b>（{@code InvoiceLinkCompensationStatusEnum} 是唯一真相源），
 * SQL 里不出现 {@code 0/1/2} 字面量。租约的<b>时间值</b>同样由调用方以参数传入（{@code now} /
 * {@code leaseUntil}），不在 SQL 里写 {@code DATE_ADD(... INTERVAL ...)} —— MySQL 与测试用 H2
 * 的日期算术方言不同，参数化后一条 SQL 两边通用，且「判定基准」与「写入值」取同一时钟，
 * 不混用应用时钟与库时钟。
 */
public interface InvoiceLinkCompensationMapper extends BaseMapper<InvoiceLinkCompensationPO> {

    /**
     * 到期待补偿记录：{@code status = PENDING AND next_retry_at <= now()}
     * <b>且当前无人持有有效租约</b>（{@code lease_until IS NULL OR lease_until < now}），
     * 按 {@code next_retry_at} 升序（老单优先收敛）至多 {@code limit} 条。
     *
     * <p><b>租约条件就是「回收」本身</b>：租约过期的行在这一条查询里自动重新出现，
     * 不需要额外任务把它从「处理中」改回来。命中索引 {@code idx_status_next_retry(status, next_retry_at)}。
     *
     * <p>{@code LIMIT} 由应用层常量传入（有界批量），不拼接外部输入，无注入面。
     *
     * @param pendingStatus 待补偿状态码（0）
     * @param now           当前时间（到期判定 + 租约过期判定基准）
     * @param limit         单轮最大条数
     * @return 到期且可认领的记录 PO 列表
     */
    @Select("SELECT * FROM invoice_link_compensation WHERE status = #{pendingStatus} "
            + "AND next_retry_at <= #{now} "
            + "AND (lease_until IS NULL OR lease_until < #{now}) "
            + "ORDER BY next_retry_at ASC, id ASC LIMIT #{limit}")
    List<InvoiceLinkCompensationPO> selectDueForRetry(@Param("pendingStatus") int pendingStatus,
                                                      @Param("now") LocalDateTime now,
                                                      @Param("limit") int limit);

    /**
     * 认领一行（{@code PENDING} 上的<b>带过期时间租约</b>）：仅当该行仍是 {@code PENDING}
     * 且<b>无人持有有效租约</b>时写入 {@code lease_until = leaseUntil}。
     *
     * <p>并发裁决交给数据库：两个执行者同时认领同一行，只有一个能命中（行锁 + {@code WHERE} 再判定），
     * 另一个命中 0 行 ⇒ 本轮跳过该行。<b>命中行数 = 1 才算认领成功</b>。
     *
     * <p>注意「过期即可再次认领」：不需要把 {@code lease_until} 显式清回 NULL 才算释放 ——
     * 时间过了这行就自动可认领（回收语义 = 无任务）。
     *
     * @param id            记录主键
     * @param pendingStatus 期望当前状态（0）
     * @param leaseUntil    租约到期时间（由调用方按租约时长算出）
     * @param now           当前时间（判断已有租约是否已过期）
     * @return 命中行数（1 = 认领成功；0 = 他人已认领或状态已推进）
     */
    @Update("UPDATE invoice_link_compensation SET lease_until = #{leaseUntil}, "
            + "update_time = CURRENT_TIMESTAMP WHERE id = #{id} AND status = #{pendingStatus} "
            + "AND (lease_until IS NULL OR lease_until < #{now})")
    int claimIfLeasable(@Param("id") Long id,
                        @Param("pendingStatus") int pendingStatus,
                        @Param("leaseUntil") LocalDateTime leaseUntil,
                        @Param("now") LocalDateTime now);

    /**
     * 条件置「已补偿」：{@code WHERE id = ? AND status = PENDING} → {@code status = DONE}，并释放租约。
     *
     * @param id            记录主键
     * @param pendingStatus 期望当前状态（0）
     * @param doneStatus    目标状态（1）
     * @return 命中行数（0 = 该行已不在 PENDING，本次不生效）
     */
    @Update("UPDATE invoice_link_compensation SET status = #{doneStatus}, lease_until = NULL, "
            + "update_time = CURRENT_TIMESTAMP "
            + "WHERE id = #{id} AND status = #{pendingStatus}")
    int markDoneIfPending(@Param("id") Long id,
                          @Param("pendingStatus") int pendingStatus,
                          @Param("doneStatus") int doneStatus);

    /**
     * 条件安排重试：{@code WHERE id = ? AND status = PENDING} → 写
     * {@code retry_count / next_retry_at / last_error}，并释放租约。
     *
     * <p>释放是必须的：退避行如果带着未到期的旧租约，下一轮即使 {@code next_retry_at} 已到期
     * 也会被租约条件挡在可认领集合之外，等于把它藏到租约到期为止（白等一轮）。
     *
     * @param id            记录主键
     * @param pendingStatus 期望当前状态（0）
     * @param retryCount    已重试次数（累计值）
     * @param nextRetryAt   下次可拾取时间（退避后）
     * @param lastError     失败摘要（调用方已按列宽截断）
     * @return 命中行数（0 = 该行已不在 PENDING）
     */
    @Update("UPDATE invoice_link_compensation SET retry_count = #{retryCount}, "
            + "next_retry_at = #{nextRetryAt}, last_error = #{lastError}, lease_until = NULL, "
            + "update_time = CURRENT_TIMESTAMP "
            + "WHERE id = #{id} AND status = #{pendingStatus}")
    int scheduleRetryIfPending(@Param("id") Long id,
                               @Param("pendingStatus") int pendingStatus,
                               @Param("retryCount") int retryCount,
                               @Param("nextRetryAt") LocalDateTime nextRetryAt,
                               @Param("lastError") String lastError);

    /**
     * 条件置「永久失败」：{@code WHERE id = ? AND status = PENDING} → {@code status = FAILED_PERMANENT}，
     * 并释放租约（终态行不携带无用租约，人工排查时租约列不会给出「仍有人在跑」的假信号）。
     *
     * @param id            记录主键
     * @param pendingStatus 期望当前状态（0）
     * @param failedStatus  目标状态（2）
     * @param lastError     终态原因摘要（调用方已按列宽截断）
     * @return 命中行数（0 = 该行已不在 PENDING）
     */
    @Update("UPDATE invoice_link_compensation SET status = #{failedStatus}, last_error = #{lastError}, "
            + "lease_until = NULL, update_time = CURRENT_TIMESTAMP "
            + "WHERE id = #{id} AND status = #{pendingStatus}")
    int markFailedPermanentIfPending(@Param("id") Long id,
                                     @Param("pendingStatus") int pendingStatus,
                                     @Param("failedStatus") int failedStatus,
                                     @Param("lastError") String lastError);
}
