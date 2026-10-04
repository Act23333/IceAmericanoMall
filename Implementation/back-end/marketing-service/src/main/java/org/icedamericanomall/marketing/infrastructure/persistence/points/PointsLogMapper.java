package org.icedamericanomall.marketing.infrastructure.persistence.points;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 积分账本 Mapper —— 入参/出参均为 PO 或基本类型。
 */
@Mapper
public interface PointsLogMapper extends BaseMapper<PointsLogPO> {

    /**
     * 积分总余额（数据库侧聚合，无流水用户返回 0）。
     */
    @Select("SELECT COALESCE(SUM(points), 0) FROM points_log WHERE user_id = #{userId}")
    long sumPointsByUserId(@Param("userId") Long userId);
}
