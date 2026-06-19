package org.icedAmericanoMall.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.icedAmericanoMall.domain.entity.CouponEntity;
import org.icedAmericanoMall.domain.entity.UserCouponEntity;
import java.util.List;

public interface CouponService extends IService<CouponEntity> {

    /** 用户领取优惠券，返回 user_coupon 记录 */
    UserCouponEntity claim(Long userId, String couponId);

    /** 查询用户可用优惠券列表 */
    List<UserCouponEntity> getUserAvailableCoupons(Long userId);

    /** 查询用户已使用/已过期优惠券 */
    List<UserCouponEntity> getUserUsedCoupons(Long userId);

    /** 使用优惠券（下单时调用），返回实际抵扣金额（分） */
    int useCoupon(Long userId, Long userCouponId, String orderNo, int orderAmount);

    /** 回滚优惠券（取消订单时调用） */
    void rollbackCoupon(Long userCouponId);
}
