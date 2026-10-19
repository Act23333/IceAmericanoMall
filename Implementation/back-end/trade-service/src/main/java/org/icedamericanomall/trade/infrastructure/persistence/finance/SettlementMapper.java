package org.icedamericanomall.trade.infrastructure.persistence.finance;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 结算单 Mapper。
 *
 * <p>{@link #settleIfPending} 是 PENDING→SETTLED 的<b>唯一</b>原子入口：可结算条件全部字段化
 * （能写成 WHERE 的东西才叫条件），一条语句 + 影响行数判定，不读-改-写、不用行锁。
 */
@Mapper
public interface SettlementMapper extends BaseMapper<SettlementPO> {

    /**
     * 条件迁移：{@code status = PENDING → SETTLED}，且必须同时满足「账期已走完 / 金额 > 0 / 有订单」。
     *
     * <pre>
     * WHERE id = ? AND status = ? AND period_end &lt;= ? AND settlement_amount &gt; 0 AND order_count &gt; 0
     * </pre>
     *
     * <p>0 行 = 条件不满足（已结算 / 未到期 / 零额 / 无订单 / 不存在）—— <b>幂等 no-op，不抛异常</b>。
     *
     * @param today 由调用方按<b>应用时钟</b>算好传入（不用 {@code CURDATE()}：MySQL/H2 的时区与
     *              「应用认为的今天」可能不是同一天，而账期判定属于业务口径，不该由数据库时钟决定）
     * @return 影响行数：1=本次真的完成结算；0=条件不满足
     */
    @Update("UPDATE settlement SET status = #{toStatus}, update_time = #{now} "
            + "WHERE id = #{id} AND status = #{fromStatus} AND period_end <= #{today} "
            + "AND settlement_amount > 0 AND order_count > 0")
    int settleIfPending(@Param("id") Long id,
                        @Param("fromStatus") Integer fromStatus,
                        @Param("toStatus") Integer toStatus,
                        @Param("today") LocalDate today,
                        @Param("now") LocalDateTime now);

    /**
     * 扫描「可结算」的结算单 id（触发入口的第一步：先取候选，再逐条走 {@link #settleIfPending}）。
     *
     * <pre>
     * WHERE status = ? AND period_end &lt;= ? AND settlement_amount &gt; 0 AND order_count &gt; 0
     * ORDER BY id LIMIT ?
     * </pre>
     *
     * <p><b>条件必须与 {@code settleIfPending} 完全一致</b>：扫描只是「缩小候选」，真正的判定仍在
     * 条件更新里（两次扫描之间状态可能已变），所以这里宽一点、严一点都不会造成错结 —— 只会多扫或漏扫。
     * 但如果两者条件不一致，就会出现「扫到却永远结不掉」的僵尸候选（每天白扫），故保持一致。
     *
     * @param limit 单批上限：调度频次交给 XXL-Job，批次大小由调用方控制，避免一次扫全表
     */
    @Select("SELECT id FROM settlement WHERE status = #{pendingStatus} AND period_end <= #{today} "
            + "AND settlement_amount > 0 AND order_count > 0 ORDER BY id LIMIT #{limit}")
    List<Long> selectSettleableIds(@Param("pendingStatus") Integer pendingStatus,
                                   @Param("today") LocalDate today,
                                   @Param("limit") int limit);
}
