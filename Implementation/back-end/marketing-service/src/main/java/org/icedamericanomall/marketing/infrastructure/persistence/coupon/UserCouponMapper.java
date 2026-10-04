package org.icedamericanomall.marketing.infrastructure.persistence.coupon;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户优惠券 Mapper —— 入参/出参均为 PO。
 */
@Mapper
public interface UserCouponMapper extends BaseMapper<UserCouponPO> {
}
