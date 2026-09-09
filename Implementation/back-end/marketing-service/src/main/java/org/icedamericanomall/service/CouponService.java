package org.icedamericanomall.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.icedamericanomall.domain.entity.CouponEntity;
import org.icedamericanomall.domain.entity.UserCouponEntity;
import java.util.List;

public interface CouponService extends IService<CouponEntity> {

    /** 用户领取优惠券（免费领取），返回 user_coupon 记录 */
    UserCouponEntity claim(Long userId, String couponId);

    /** 查询用户可用优惠券列表 */
    List<UserCouponEntity> getUserAvailableCoupons(Long userId);

    /** 查询用户已使用/已过期优惠券 */
    List<UserCouponEntity> getUserUsedCoupons(Long userId);

    /** V4.2: 使用优惠券（下单时调用），返回实际抵扣金额（分）。新增 orderType+sellerId 用于类别/店铺校验 */
    int useCoupon(Long userId, Long userCouponId, String orderNo, int orderAmount,
                  Integer orderType, Long sellerId);

    /** 回滚优惠券（取消订单时调用） */
    void rollbackCoupon(Long userCouponId);

    /** 按订单号回滚优惠券（下单失败/取消/超时补偿，幂等）。 */
    void rollbackByOrderNo(String orderNo);

    // === V4.0: 多维度优惠券模型 ===

    /** V4.0: 购买付费优惠券（创建PRE_PAID状态的user_coupon记录，status=4） */
    UserCouponEntity purchaseCoupon(Long userId, String couponId);

    /** V4.0: 秒杀级优惠券领取（Redis Lua脚本，高并发，grabType=NEED_GRAB时使用） */
    UserCouponEntity claimWithGrab(Long userId, String couponId);
}
