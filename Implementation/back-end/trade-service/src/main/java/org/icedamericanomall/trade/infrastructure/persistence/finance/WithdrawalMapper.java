package org.icedamericanomall.trade.infrastructure.persistence.finance;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 提现单 Mapper。
 *
 * <p>{@link #reviewIfPending} 是审核（待审核 → 已打款/拒绝）的<b>条件迁移</b>：
 * 状态迁移一律写 {@code WHERE id = ? AND status = ?}，不读-改-写。
 */
@Mapper
public interface WithdrawalMapper extends BaseMapper<WithdrawalPO> {

    /**
     * 受理出款：待审核 → 付款中，并落幂等键（条件更新，0 行 = 已受理/已终态）。
     *
     * @param id       提现单主键
     * @param payoutNo 出款幂等键（全生命周期不变；重试复用同一个）
     * @return 影响行数
     */
    @Update("UPDATE withdrawal SET status = 4, payout_no = #{payoutNo} "
            + "WHERE id = #{id} AND status = 1")
    int claimPaying(@Param("id") Long id, @Param("payoutNo") String payoutNo);

    /**
     * 回写已打款：付款中 → 已打款（0 行 = 状态已被改过，调用方不得再记账）。
     *
     * @param id 提现单主键
     * @return 影响行数
     */
    @Update("UPDATE withdrawal SET status = 2 WHERE id = #{id} AND status = 4")
    int markPaidIfPaying(@Param("id") Long id);

    /**
     * 取「付款中」的单（按主键升序，限量）。
     *
     * @param limit 上限
     * @return 主键列表
     */
    /**
     * 取「到期待重试」的付款中单据：从未尝试过，或距上次尝试已超过节流窗口。
     *
     * <p><b>为什么不用 create_time</b>：那会把**首次**出款也推迟一个窗口 —— 那不是节流，是延迟到账。
     */
    @Select("SELECT id FROM withdrawal WHERE status = 4 "
            + "AND (last_payout_attempt_at IS NULL OR last_payout_attempt_at < #{cutBefore}) "
            + "ORDER BY id ASC LIMIT #{limit}")
    java.util.List<Long> selectDuePayingIds(@Param("cutBefore") java.time.LocalDateTime cutBefore,
                                            @Param("limit") int limit);

    /**
     * 标记「本次尝试」（事务 A，独立提交）：节流用，不改变业务状态。
     *
     * @param id 提现单主键
     * @param at 尝试时间（与扫描同一个 now）
     * @return 影响行数；0 = 状态已变，调用方必须跳过
     */
    @Update("UPDATE withdrawal SET last_payout_attempt_at = #{at} WHERE id = #{id} AND status = 4")
    int markPayoutAttempt(@Param("id") Long id, @Param("at") java.time.LocalDateTime at);

    /** 记录上次出款结果（分类:渠道号，超长截断并标记）。 */
    @org.apache.ibatis.annotations.Update("UPDATE withdrawal SET last_payout_result = #{result} WHERE id = #{id}")
    int markPayoutResult(@Param("id") Long id, @Param("result") String result);

    /** 读上次出款结果（供告警体；NULL = 从未尝试）。 */
    @Select("SELECT last_payout_result FROM withdrawal WHERE withdrawal_no = #{payoutNo}")
    String selectPayoutResult(@Param("payoutNo") String payoutNo);

    /**
     * 告警去重账：同一 payout_no + 代次只落一行（多实例共用）。
     *
     * @return 1 = 首次（应当告警）；0 = 已告过
     */
    /**
     * 按保留期清理告警日志（一次一句 DELETE，不分批循环）。
     *
     * <p>为什么不带 LIMIT：告警日志的增长速率与「停留超阈值的出款单」正相关，量级很小；
     * 引入分批反而带来「删一半」的中间态。真到需要分批时再按 id 区间切，并有测试兜着改。
     *
     * @param before 早于此时刻的记录被删除
     * @return 删除行数
     */
    @org.apache.ibatis.annotations.Delete("DELETE FROM payout_alert_log WHERE create_time < #{before}")
    int deleteAlertLogBefore(@Param("before") java.time.LocalDateTime before);

    @Update("INSERT IGNORE INTO payout_alert_log(payout_no, alert_gen) VALUES(#{payoutNo}, #{gen})")
    int markAlertedOnce(@Param("payoutNo") String payoutNo, @Param("gen") int gen);

    /**
     * 取「停留超过阈值」的付款中单据（供告警扫描）。
     *
     * @param cutBefore 时间下界（create_time 早于它才算停留过久）
     * @param limit     上限
     * @return 提现单 PO 列表（只取告警需要的列）
     */
    @Select("SELECT id, withdrawal_no, payout_no, seller_id, amount, create_time FROM withdrawal "
            + "WHERE status = 4 AND create_time < #{cutBefore} ORDER BY id ASC LIMIT #{limit}")
    java.util.List<WithdrawalPO> selectStalePaying(@org.apache.ibatis.annotations.Param("cutBefore") java.time.LocalDateTime cutBefore,
                                                   @Param("limit") int limit);

    /**
     * 审核提现：{@code status = 待审核 → 目标状态}，并记录审核备注。
     *
     * <p>0 行 = 该单已不是「待审核」（重复审核 / 并发双击 / 已被驳回）—— <b>幂等 no-op，不改任何字段</b>。
     * 调用方据此直接返回，避免把同一笔打款在资金账户上记两遍。
     *
     * @return 影响行数：1=本次真的完成审核；0=状态已不是待审核
     */
    @Update("UPDATE withdrawal SET status = #{toStatus}, admin_remark = #{adminRemark}, update_time = NOW() "
            + "WHERE id = #{id} AND status = #{fromStatus}")
    int reviewIfPending(@Param("id") Long id,
                        @Param("fromStatus") Integer fromStatus,
                        @Param("toStatus") Integer toStatus,
                        @Param("adminRemark") String adminRemark);
}
