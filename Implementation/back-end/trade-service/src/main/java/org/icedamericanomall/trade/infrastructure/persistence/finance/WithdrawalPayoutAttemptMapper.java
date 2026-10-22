package org.icedamericanomall.trade.infrastructure.persistence.finance;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 出款尝试账 Mapper。
 *
 * <p>{@link #lockWithdrawal} 是整个不变量（同一提现单最多一个非终态尝试）的地基：
 * 占号前必须先锁提现单行，否则"先查后插"在并发下会各插一行。
 */
@Mapper
public interface WithdrawalPayoutAttemptMapper extends BaseMapper<WithdrawalPayoutAttemptPO> {

    /**
     * 锁提现单行（{@code FOR UPDATE}，必须在事务内调用）。
     *
     * @return 行存在时返回其 id；不存在返回 null（调用方据此判"提现单不存在"）
     */
    @Select("SELECT id, withdrawal_no FROM withdrawal WHERE id = #{withdrawalId} FOR UPDATE")
    WithdrawalPO lockWithdrawal(@Param("withdrawalId") Long withdrawalId);

    /** 当前最大尝试序号（无尝试时为 0）。 */
    @Select("SELECT COALESCE(MAX(attempt_no), 0) FROM withdrawal_payout_attempt WHERE withdrawal_id = #{withdrawalId}")
    int selectMaxAttemptNo(@Param("withdrawalId") Long withdrawalId);

    /** 取未收敛（非终态）的尝试：最多应有一条。 */
    @Select("SELECT * FROM withdrawal_payout_attempt WHERE withdrawal_id = #{withdrawalId} "
            + "AND state IN (#{requested}, #{unknown}) ORDER BY id DESC LIMIT 1")
    WithdrawalPayoutAttemptPO selectNonTerminal(@Param("withdrawalId") Long withdrawalId,
                                                @Param("requested") int requested,
                                                @Param("unknown") int unknown);

    /** 统计已达明确失败状态的尝试数（重试上限只数这一种）。 */
    @Select("SELECT COUNT(*) FROM withdrawal_payout_attempt WHERE withdrawal_id = #{withdrawalId} AND state = #{state}")
    int countByState(@Param("withdrawalId") Long withdrawalId, @Param("state") int state);

    /** 条件状态更新：0 行 = 状态已变或单号不存在。 */
    @Update("UPDATE withdrawal_payout_attempt SET state = #{to}, finished_at = #{finishedAt} "
            + "WHERE payout_no = #{payoutNo} AND state = #{from}")
    int updateStateConditional(@Param("payoutNo") String payoutNo, @Param("from") int from,
                               @Param("to") int to, @Param("finishedAt") java.time.LocalDateTime finishedAt);

    /** 按单号查（{@code query()} 写回入口）。 */
    @Select("SELECT * FROM withdrawal_payout_attempt WHERE payout_no = #{payoutNo}")
    WithdrawalPayoutAttemptPO selectByPayoutNo(@Param("payoutNo") String payoutNo);
}
