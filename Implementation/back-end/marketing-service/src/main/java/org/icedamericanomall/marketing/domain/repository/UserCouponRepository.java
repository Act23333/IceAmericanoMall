package org.icedamericanomall.marketing.domain.repository;

import org.icedamericanomall.marketing.domain.model.coupon.UserCoupon;

import java.util.List;

/**
 * 用户持券仓储（领域端口）—— 实现方：{@code infrastructure.persistence.coupon.UserCouponRepositoryImpl}。
 */
public interface UserCouponRepository {

    /** 按主键取券（不存在返回 null）。 */
    UserCoupon findById(Long id);

    /** 新增（id 回填）。 */
    UserCoupon save(UserCoupon userCoupon);

    /** 全量更新（核销/回滚）。 */
    void update(UserCoupon userCoupon);

    /** 用户对该券的持有数（领取去重的 DB 兜底）。 */
    long countByUserIdAndCouponId(Long userId, Long couponId);

    /** 未使用券列表。 */
    List<UserCoupon> findUnusedByUserId(Long userId);

    /** 已使用/已过期券列表（按使用时间倒序）。 */
    List<UserCoupon> findUsedByUserId(Long userId);

    /** 按订单号取已使用券（回滚用；不存在返回 null）。 */
    UserCoupon findUsedByOrderNo(String orderNo);
}
