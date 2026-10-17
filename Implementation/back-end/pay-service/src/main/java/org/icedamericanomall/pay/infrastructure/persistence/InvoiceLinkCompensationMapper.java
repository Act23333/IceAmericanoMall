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
 * <p><b>除 BaseMapper 标准方法外，四条显式 SQL</b>：补偿重试任务的读侧筛选口径
 * （{@code status + next_retry_at + 限量}）与三条状态迁移（{@code PENDING → DONE / 退避 / → 永久失败}）
 * 都是<b>契约级不变式</b>，收口在 Mapper 层显式可审计 —— 与 {@link RefundOrderMapper#selectStaleProcessing}
 * 同口径（筛选口径不在 Wrapper 里拼），与 trade {@code AfterSaleMapper.updateStatusConditionally} 同理。
 *
 * <p><b>为什么状态迁移必须写在 SQL 的 {@code WHERE} 里</b>：调度器可能被重复/并发触发
 * （同包既有 Job 均未加分布式锁，见 {@code InvoiceLinkCompensationRetryJob} 类注释）。
 * 「先查后写」的读-改-写在并发下会双写成功，导致同一补偿被补偿两次、或终态被退避回
 * {@code PENDING}。条件更新让数据库做唯一裁决：命中行数 &gt; 0 才算本次推进生效。
 *
 * <p><b>状态码一律由调用方以参数传入</b>（{@code InvoiceLinkCompensationStatusEnum} 是唯一真相源），
 * SQL 里不出现 {@code 0/1/2} 字面量。
 */
public interface InvoiceLinkCompensationMapper extends BaseMapper<InvoiceLinkCompensationPO> {

    /**
     * 到期待补偿记录：{@code status = PENDING AND next_retry_at <= now()}，
     * 按 {@code next_retry_at} 升序（老单优先收敛）至多 {@code limit} 条。
     *
     * <p>{@code LIMIT} 由应用层常量传入（有界批量），不拼接外部输入，无注入面。
     * 命中索引 {@code idx_status_next_retry(status, next_retry_at)}。
     *
     * @param pendingStatus 待补偿状态码（0）
     * @param now           当前时间（到期判定基准）
     * @param limit         单轮最大条数
     * @return 到期记录 PO 列表
     */
    @Select("SELECT * FROM invoice_link_compensation WHERE status = #{pendingStatus} "
            + "AND next_retry_at <= #{now} ORDER BY next_retry_at ASC, id ASC LIMIT #{limit}")
    List<InvoiceLinkCompensationPO> selectDueForRetry(@Param("pendingStatus") int pendingStatus,
                                                      @Param("now") LocalDateTime now,
                                                      @Param("limit") int limit);

    /**
     * 条件置「已补偿」：{@code WHERE id = ? AND status = PENDING} → {@code status = DONE}。
     *
     * @param id            记录主键
     * @param pendingStatus 期望当前状态（0）
     * @param doneStatus    目标状态（1）
     * @return 命中行数（0 = 该行已不在 PENDING，本次不生效）
     */
    @Update("UPDATE invoice_link_compensation SET status = #{doneStatus}, update_time = CURRENT_TIMESTAMP "
            + "WHERE id = #{id} AND status = #{pendingStatus}")
    int markDoneIfPending(@Param("id") Long id,
                          @Param("pendingStatus") int pendingStatus,
                          @Param("doneStatus") int doneStatus);

    /**
     * 条件安排重试：{@code WHERE id = ? AND status = PENDING} → 写 {@code retry_count / next_retry_at / last_error}。
     *
     * @param id            记录主键
     * @param pendingStatus 期望当前状态（0）
     * @param retryCount    已重试次数（累计值）
     * @param nextRetryAt   下次可拾取时间（退避后）
     * @param lastError     失败摘要（调用方已按列宽截断）
     * @return 命中行数（0 = 该行已不在 PENDING）
     */
    @Update("UPDATE invoice_link_compensation SET retry_count = #{retryCount}, "
            + "next_retry_at = #{nextRetryAt}, last_error = #{lastError}, update_time = CURRENT_TIMESTAMP "
            + "WHERE id = #{id} AND status = #{pendingStatus}")
    int scheduleRetryIfPending(@Param("id") Long id,
                               @Param("pendingStatus") int pendingStatus,
                               @Param("retryCount") int retryCount,
                               @Param("nextRetryAt") LocalDateTime nextRetryAt,
                               @Param("lastError") String lastError);

    /**
     * 条件置「永久失败」：{@code WHERE id = ? AND status = PENDING} → {@code status = FAILED_PERMANENT}。
     *
     * @param id            记录主键
     * @param pendingStatus 期望当前状态（0）
     * @param failedStatus  目标状态（2）
     * @param lastError     终态原因摘要（调用方已按列宽截断）
     * @return 命中行数（0 = 该行已不在 PENDING）
     */
    @Update("UPDATE invoice_link_compensation SET status = #{failedStatus}, last_error = #{lastError}, "
            + "update_time = CURRENT_TIMESTAMP WHERE id = #{id} AND status = #{pendingStatus}")
    int markFailedPermanentIfPending(@Param("id") Long id,
                                     @Param("pendingStatus") int pendingStatus,
                                     @Param("failedStatus") int failedStatus,
                                     @Param("lastError") String lastError);
}
