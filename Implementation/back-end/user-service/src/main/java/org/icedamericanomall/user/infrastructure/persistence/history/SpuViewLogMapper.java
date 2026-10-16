package org.icedamericanomall.user.infrastructure.persistence.history;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.icedamericanomall.user.infrastructure.persistence.history.SpuViewLogPO;

@Mapper
public interface SpuViewLogMapper extends BaseMapper<SpuViewLogPO> {

    /** 记录浏览（重复浏览更新 view_time） */
    @Insert("INSERT INTO spu_view_log (user_id, spu_id, view_time) VALUES (#{userId}, #{spuId}, NOW()) "
            + "ON DUPLICATE KEY UPDATE view_time = NOW()")
    int recordView(@Param("userId") Long userId, @Param("spuId") Long spuId);
}
