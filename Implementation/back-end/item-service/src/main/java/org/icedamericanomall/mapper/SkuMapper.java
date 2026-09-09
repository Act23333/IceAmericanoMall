package org.icedamericanomall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import org.icedamericanomall.domain.entity.SkuEntity;

public interface SkuMapper extends BaseMapper<SkuEntity> {

    /** 原子扣库存: UPDATE sku SET stock = stock - #{qty}, version = version + 1
     *  WHERE id = #{id} AND stock >= #{qty} */
    @Update("UPDATE sku SET stock = stock - #{qty}, sold_count = sold_count + #{qty}, "
            + "version = version + 1, update_time = NOW() "
            + "WHERE id = #{id} AND stock >= #{qty} AND status = 1")
    int deductStock(@Param("id") Long id, @Param("qty") int qty);

    /** 原子恢复库存 */
    @Update("UPDATE sku SET stock = stock + #{qty}, sold_count = GREATEST(0, sold_count - #{qty}), "
            + "version = version + 1, update_time = NOW() "
            + "WHERE id = #{id}")
    int restoreStock(@Param("id") Long id, @Param("qty") int qty);
}
