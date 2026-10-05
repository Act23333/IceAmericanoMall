package org.icedamericanomall.user.infrastructure.persistence.member;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 会员成长值流水 Mapper（uk_order_no 幂等锚点 —— ADR-044）。
 */
@Mapper
public interface MemberGrowthLogMapper extends BaseMapper<MemberGrowthLogPO> {

    /**
     * 用户累计成长值（{@code Σ growth_amount}，分）。
     *
     * <p>显式 SQL 口径：SUM 聚合不冗余存列，以流水求和为唯一事实源（IFNULL 兜底无流水返回 0）。
     *
     * @param userId 用户技术主键
     * @return 累计成长值（分，≥ 0）
     */
    @Select("SELECT IFNULL(SUM(growth_amount), 0) FROM member_growth_log WHERE user_id = #{userId}")
    long sumGrowthByUserId(@Param("userId") Long userId);
}
