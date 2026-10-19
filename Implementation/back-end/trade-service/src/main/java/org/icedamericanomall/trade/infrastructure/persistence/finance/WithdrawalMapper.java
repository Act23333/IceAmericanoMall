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
    @Select("SELECT id FROM withdrawal WHERE status = 4 ORDER BY id ASC LIMIT #{limit}")
    java.util.List<Long> selectPayingIds(@Param("limit") int limit);

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
