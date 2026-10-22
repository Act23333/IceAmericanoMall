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

    /**
     * 打标 + 写派生指针（同一条 UPDATE，在 claimAndMark 的事务里执行）。
     *
     * <p>条件带 {@code status = 4}（PAYING）：状态已变则不写 —— 避免把指针挂到一张已终结的单上。
     */
    @Update("UPDATE withdrawal SET last_payout_attempt_at = #{at}, payout_no = #{payoutNo} "
            + "WHERE id = #{id} AND status = 4")
    int markAttemptAndPointer(@Param("id") Long id, @Param("at") java.time.LocalDateTime at,
                              @Param("payoutNo") String payoutNo);

    /** 条件状态更新：0 行 = 状态已变或单号不存在。 */
    @Update("UPDATE withdrawal_payout_attempt SET state = #{to}, finished_at = #{finishedAt} "
            + "WHERE payout_no = #{payoutNo} AND state = #{from}")
    int updateStateConditional(@Param("payoutNo") String payoutNo, @Param("from") int from,
                               @Param("to") int to, @Param("finishedAt") java.time.LocalDateTime finishedAt);

    /** 提现单当前状态（用于把"状态已变"写进异常信息，免去值班再查一次库）。 */
    @Select("SELECT status FROM withdrawal WHERE id = #{id}")
    Integer selectWithdrawalStatus(@Param("id") Long id);

    /** 按单号查（{@code query()} 写回入口）。 */
    @Select("SELECT * FROM withdrawal_payout_attempt WHERE payout_no = #{payoutNo}")
    WithdrawalPayoutAttemptPO selectByPayoutNo(@Param("payoutNo") String payoutNo);
}
