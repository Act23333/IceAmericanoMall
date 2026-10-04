package org.icedamericanomall.user.infrastructure.persistence.history;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.icedamericanomall.user.infrastructure.persistence.history.ProductViewLogPO;

@Mapper
public interface ProductViewLogMapper extends BaseMapper<ProductViewLogPO> {

    /** 记录浏览（重复浏览更新 view_time） */
    @Insert("INSERT INTO product_view_log (user_id, product_id, view_time) VALUES (#{userId}, #{productId}, NOW()) "
            + "ON DUPLICATE KEY UPDATE view_time = NOW()")
    int recordView(@Param("userId") Long userId, @Param("productId") Long productId);
}
