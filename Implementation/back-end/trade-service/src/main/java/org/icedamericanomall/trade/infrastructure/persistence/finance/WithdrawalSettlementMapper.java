package org.icedamericanomall.trade.infrastructure.persistence.finance;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 提现核销明细 Mapper。
 *
 * <p>只做「某笔提现的全部明细」这一种读取：核销释放要按明细逐行回退 {@code withdrawn_amount}，
 * 所以必须先取出来再删 —— 删完就查不到「该退多少」。
 */
@Mapper
public interface WithdrawalSettlementMapper extends BaseMapper<WithdrawalSettlementPO> {

    /** 该提现单的全部核销明细（按主键升序，便于日志与对账复算）。 */
    @Select("SELECT id, withdrawal_id, settlement_id, amount, create_time FROM withdrawal_settlement "
            + "WHERE withdrawal_id = #{withdrawalId} ORDER BY id")
    List<WithdrawalSettlementPO> selectByWithdrawalId(@Param("withdrawalId") Long withdrawalId);

    /** 删除该提现单的全部明细（释放）；返回删除行数。 */
    @Delete("DELETE FROM withdrawal_settlement WHERE withdrawal_id = #{withdrawalId}")
    int deleteByWithdrawalId(@Param("withdrawalId") Long withdrawalId);
}
