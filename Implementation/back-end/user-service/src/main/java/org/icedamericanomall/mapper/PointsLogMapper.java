package org.icedamericanomall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.icedamericanomall.domain.entity.PointsLogEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface PointsLogMapper extends BaseMapper<PointsLogEntity> {

    /** 数据库侧聚合用户积分余额，避免全表加载内存求和。 */
    @Select("SELECT COALESCE(SUM(points), 0) FROM points_log WHERE user_id = #{userId}")
    long sumPointsByUserId(@Param("userId") Long userId);
}
