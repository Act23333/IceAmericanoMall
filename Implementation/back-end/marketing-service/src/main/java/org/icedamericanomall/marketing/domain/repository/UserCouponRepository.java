package org.icedamericanomall.marketing.domain.repository;

import org.icedamericanomall.marketing.domain.enums.UserCouponStatusEnum;
import org.icedamericanomall.marketing.domain.model.coupon.UserCoupon;

import java.util.List;

/**
 * 用户持券仓储（领域端口）—— 实现方：{@code infrastructure.persistence.coupon.UserCouponRepositoryImpl}。
 */
public interface UserCouponRepository {

    /** 按主键取券（不存在返回 null）。 */
    UserCoupon findById(Long id);

    /**
     * 按主键列表批量取券（I46 叠加信息批量查询，避免 N+1）。
     *
     * @param ids 持券主键列表（{@code null}/空 → 空列表；不存在的 id 自然不在结果中）
     * @return 命中的持券列表（顺序不保证，调用方不应依赖）
     */
    List<UserCoupon> findByIds(List<Long> ids);

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

    /**
     * 用户持券按状态计数（M7 我的券三态计数，V5.11）。
     *
     * <p>状态口径与列表查询一致：{@code user_coupon.status} 落库态
     * （1未使用/2已使用/3已过期，{@link UserCouponStatusEnum}）。
     *
     * @param userId 用户 ID
     * @param status 持券状态
     * @return 该状态下持券数量（无则 0）
     */
    long countByUserIdAndStatus(Long userId, UserCouponStatusEnum status);
}
