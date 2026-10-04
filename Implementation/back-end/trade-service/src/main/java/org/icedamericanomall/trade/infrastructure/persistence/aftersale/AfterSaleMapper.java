package org.icedamericanomall.trade.infrastructure.persistence.aftersale;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import org.icedamericanomall.trade.infrastructure.persistence.aftersale.AfterSalePO;

import java.util.List;

@Mapper
public interface AfterSaleMapper extends BaseMapper<AfterSalePO> {

    /**
     * 售后单状态「条件迁移」—— 仅当当前状态等于期望值时推进，返回受影响行数（0 = 未命中）。
     *
     * <p>用显式 SQL 而非 MP 的 {@code lambdaUpdate()} 链：条件更新是售后状态机的唯一安全出口
     * （并发审核 / 退款回调下只有一个生效），显式 SQL 把「WHERE 带期望状态」这一不变量
     * 落到 Mapper 契约上——可读、可单测，且不受 MP 代理在单元测试中不可用的限制。
     *
     * @param id             售后单 ID
     * @param expectedStatus 期望的当前状态（不匹配则不更新）
     * @param targetStatus   目标状态
     * @return 受影响行数（1 = 命中并迁移；0 = 状态已变化 / 不匹配）
     */
    @Update("UPDATE after_sale SET status = #{targetStatus}, update_time = NOW() "
            + "WHERE id = #{id} AND status = #{expectedStatus}")
    int updateStatusConditionally(@Param("id") Long id,
                                  @Param("expectedStatus") int expectedStatus,
                                  @Param("targetStatus") int targetStatus);

    /**
     * 售后单状态「多来源条件迁移」—— 当前状态命中 {@code IN (expectedStatuses)} 时才推进。
     *
     * <p><b>为何需要多来源</b>（V5.6 退款触发时机纠正，ADR-037 修订 ADR-035）：退款成功回写
     * {@code REFUNDED(5)} 的来源有两条边——仅退款走 {@code APPROVED(2)}，退货退款走
     * {@code RETURNED(4)}（商家确认收到退货后才发起退款）。两条边共用一个目标状态，
     * 用显式 {@code IN} 条件更新表达，仍保持「0 行 = 幂等空操作」的语义。
     *
     * @param id               售后单 ID
     * @param expectedStatuses 可接受的当前状态集合（非空）
     * @param targetStatus     目标状态
     * @return 受影响行数（1 = 命中并迁移；0 = 状态已迁移 / 不属于任一来源）
     */
    @Update("""
            <script>
            UPDATE after_sale SET status = #{targetStatus}, update_time = NOW()
            WHERE id = #{id} AND status IN
            <foreach collection="expectedStatuses" item="status" open="(" separator="," close=")">#{status}</foreach>
            </script>
            """)
    int updateStatusConditionallyFrom(@Param("id") Long id,
                                      @Param("expectedStatuses") List<Integer> expectedStatuses,
                                      @Param("targetStatus") int targetStatus);
}
