package org.icedamericanomall.trade.infrastructure.persistence.order;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.icedamericanomall.trade.infrastructure.persistence.order.OrderItemPO;

import java.util.List;

@Mapper
public interface OrderItemMapper extends BaseMapper<OrderItemPO> {

    /**
     * 把指定订单明细标记为「已归入某包裹」（V5.6 拆单发货，F3）。
     *
     * <p><b>为何用显式 SQL 而非 {@code lambdaUpdate()} 链</b>：条件更新是拆单发货的并发安全出口
     * （同一明细只能被一个包裹标记一次），显式 SQL 把「{@code package_no IS NULL}」这一不变量
     * 落到 Mapper 契约上——可读、可单测，且不受 MP 代理在单元测试中不可用的限制。
     *
     * @param orderId   订单 ID（范围收敛，避免跨单误标）
     * @param itemIds   本次包裹的订单项 ID（已由服务层校验归属与未发货）
     * @param packageNo 包裹号
     * @return 受影响行数（应等于 {@code itemIds.size()}；不足 = 并发下已被其它请求发货）
     */
    @Update("""
            <script>
            UPDATE order_item SET package_no = #{packageNo}, update_time = NOW()
            WHERE order_id = #{orderId} AND package_no IS NULL
              AND id IN
              <foreach collection="itemIds" item="itemId" open="(" separator="," close=")">#{itemId}</foreach>
            </script>
            """)
    int markPackageShipped(@Param("orderId") Long orderId,
                           @Param("itemIds") List<Long> itemIds,
                           @Param("packageNo") int packageNo);

    /**
     * 统计订单下仍<b>未发货</b>（{@code package_no IS NULL}）的明细数。
     *
     * <p>用于判定「全部明细已发货 → 订单转待收货；部分发货 → 保持待发货」。
     *
     * @param orderId 订单 ID
     * @return 未发货明细数
     */
    @Select("SELECT COUNT(*) FROM order_item WHERE order_id = #{orderId} AND package_no IS NULL")
    int countUnshipped(@Param("orderId") Long orderId);
}
