package org.icedamericanomall.trade.infrastructure.persistence.invoice;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 常用发票抬头 Mapper —— 默认抬头唯一语义（{@code uk_user_default} 多 NULL）在此承载。
 */
@Mapper
public interface InvoiceTitleMapper extends BaseMapper<InvoiceTitlePO> {

    /**
     * 清除用户默认抬头标记（is_default→NULL）—— 换设默认的前置步，
     * 保证「一用户至多一条默认抬头」不变量。
     *
     * @param userId 用户 user_id
     * @return 受影响行数（0/1）
     */
    @Update("UPDATE invoice_title SET is_default = NULL, update_time = NOW() "
            + "WHERE user_id = #{userId} AND is_default = 1")
    int clearDefaultByUserId(@Param("userId") Long userId);
}
