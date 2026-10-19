package org.icedamericanomall.trade.infrastructure.persistence.finance;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
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
