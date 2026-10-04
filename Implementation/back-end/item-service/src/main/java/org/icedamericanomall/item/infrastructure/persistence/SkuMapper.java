package org.icedamericanomall.item.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import org.icedamericanomall.item.infrastructure.persistence.SkuPO;

public interface SkuMapper extends BaseMapper<SkuPO> {

    /**
     * 原子扣库存（限量 SKU）：库存与销量在单条 SQL 内完成校验与变更，天然防超卖。
     * 受影响行数为 0 表示库存不足、SKU 已停售或 SKU 不存在。
     */
    @Update("UPDATE sku SET stock = stock - #{qty}, sold_count = sold_count + #{qty}, "
            + "version = version + 1, update_time = NOW() "
            + "WHERE id = #{id} AND stock >= #{qty} AND status = 1")
    int deductStock(@Param("id") Long id, @Param("qty") int qty);

    /**
     * 原子恢复库存（限量 SKU）：取消订单 / 超时回滚时使用。
     * 受影响行数为 0 表示 SKU 已不存在（幂等跳过）。
     */
    @Update("UPDATE sku SET stock = stock + #{qty}, sold_count = GREATEST(0, sold_count - #{qty}), "
            + "version = version + 1, update_time = NOW() "
            + "WHERE id = #{id}")
    int restoreStock(@Param("id") Long id, @Param("qty") int qty);

    /** 不限量 SKU 扣减：不校验库存，仅累加销量 */
    @Update("UPDATE sku SET sold_count = sold_count + #{qty}, "
            + "version = version + 1, update_time = NOW() "
            + "WHERE id = #{id} AND status = 1")
    int increaseSoldCount(@Param("id") Long id, @Param("qty") int qty);

    /** 不限量 SKU 恢复：不校验库存，仅回退销量 */
    @Update("UPDATE sku SET sold_count = GREATEST(0, sold_count - #{qty}), "
            + "version = version + 1, update_time = NOW() "
            + "WHERE id = #{id}")
    int decreaseSoldCount(@Param("id") Long id, @Param("qty") int qty);
}
