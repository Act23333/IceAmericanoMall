package org.icedamericanomall.marketing.app.coupon;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.marketing.adapter.response.UserCouponVO;
import org.icedamericanomall.marketing.domain.enums.CouponStatusEnum;
import org.icedamericanomall.marketing.domain.enums.CouponStockTypeEnum;
import org.icedamericanomall.marketing.domain.enums.GrabTypeEnum;
import org.icedamericanomall.marketing.domain.model.coupon.Coupon;
import org.icedamericanomall.marketing.domain.model.coupon.UserCoupon;
import org.icedamericanomall.marketing.domain.port.CouponClaimDedupStore;
import org.icedamericanomall.marketing.domain.port.CouponGrabStore;
import org.icedamericanomall.marketing.domain.repository.CouponRepository;
import org.icedamericanomall.marketing.domain.repository.UserCouponRepository;
import org.icedamericanomall.marketing.infrastructure.constant.MarketingRedisKeys;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 优惠券领取用例族编排（应用层 · 用户侧领取/发券）。
 *
 * <p>五维模型（discountType × couponCategory × grantType × stockType × grabType）中的「获取」侧：
 * 普通领取走 Redis 去重 + DB 兜底 + 原子条件扣减；秒杀抢券走 Lua 原子预扣并带 DB 失败补偿
 * （回滚 Redis 预扣）；支付后发券由 pay-service 回调触发。
 *
 * <p>事务口径与原实现一致：普通领取/发券整体事务；抢券路径不加事务——
 * DB 落库失败时靠显式回滚 Redis 补偿，不能依赖事务回滚兜底（Redis 在库外）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CouponClaimManager {

    private final CouponRepository couponRepository;
    private final UserCouponRepository userCouponRepository;
    private final CouponClaimDedupStore couponClaimDedupStore;
    private final CouponGrabStore couponGrabStore;
    private final UserCouponViewAssembler userCouponViewAssembler;

    /**
     * 智能领取：按 {@code grabType} 自动路由 Redis Lua 通道或普通领取通道。
     *
     * <p>NEED_GRAB 券路由 {@link #claimWithGrab}；PAID_PURCHASE 券走 trade 订单→支付→
     * {@link #grantAfterPayment} 通道，不走此处。
     */
    public UserCouponVO smartClaim(Long userId, String couponId) {
        Coupon coupon = findByCouponId(couponId);
        if (coupon.isGrabType()) {
            return claimWithGrab(userId, couponId);
        }
        return claim(userId, couponId);
    }

    /**
     * 普通领取优惠券（免费券、不限量券），Redis SISMEMBER 去重保护 DB。
     */
    @Transactional(rollbackFor = Exception.class)
    public UserCouponVO claim(Long userId, String couponId) {
        Coupon coupon = requireClaimableCoupon(couponId);
        Duration ttl = MarketingRedisKeys.ttlUntil(coupon.getEndTime());

        // Redis 快速去重（DB 侧仍有持券兜底，防止 Redis 数据丢失导致重复领）
        if (couponClaimDedupStore.tryDedup(coupon.getId(), userId, ttl) == 0) {
            throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "已领取过该优惠券");
        }
        if (userCouponRepository.countByUserIdAndCouponId(userId, coupon.getId()) > 0) {
            throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "已领取过该优惠券");
        }

        // 限量券：原子条件扣减 issued_qty；UNLIMITED 券跳过总量校验
        if (coupon.getStockType() == null
                || coupon.getStockType() == CouponStockTypeEnum.LIMITED.getCode()) {
            boolean incremented = couponRepository.incrementIssuedQtyIfBelowTotal(
                    coupon.getId(), coupon.getTotalQty());
            if (!incremented) {
                throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "优惠券已领完");
            }
        }
        return userCouponViewAssembler.assemble(insertUserCoupon(userId, coupon.getId()), coupon);
    }

    /**
     * 秒杀级优惠券领取（Redis Lua 原子操作 + DB 落库）。
     *
     * <p>仅 grabType=NEED_GRAB 的优惠券走此通道。Redis 预扣成功后若 DB 落库失败，
     * 立即回滚 Redis（INCR 库存 + SREM 去重），避免「Redis 扣了、DB 没有」导致名额永久缺失。
     */
    public UserCouponVO claimWithGrab(Long userId, String couponId) {
        Coupon coupon = requireClaimableCoupon(couponId);
        if (coupon.getGrabType() == null || coupon.getGrabType() != GrabTypeEnum.NEED_GRAB.getCode()) {
            throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "该优惠券无需抢，请使用普通领取接口");
        }

        // Redis Lua 原子预扣
        Duration ttl = MarketingRedisKeys.ttlUntil(coupon.getEndTime());
        long result = couponGrabStore.tryClaim(coupon.getId(), userId, ttl);
        if (result == CouponGrabStore.SOLD_OUT) {
            throw new BizException(ErrorCode.FLASH_SALE_SOLD_OUT, "优惠券已抢光");
        }
        if (result == CouponGrabStore.DUPLICATE) {
            throw new BizException(ErrorCode.FLASH_SALE_LIMIT_EXCEEDED, "已领取过该优惠券");
        }

        UserCoupon uc;
        try {
            uc = insertUserCoupon(userId, coupon.getId());
        } catch (Exception e) {
            // 补偿：DB 落库失败必须归还 Redis 名额，否则该券永久少一个库存
            log.error("抢券落库失败，回滚 Redis 预扣: couponId={}, userId={}", coupon.getId(), userId, e);
            couponGrabStore.rollbackClaim(coupon.getId(), userId);
            throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "优惠券领取失败，请重试");
        }

        // 同步更新 MySQL 的 issued_qty（非关键路径，失败不影响领取）
        try {
            couponRepository.incrementIssuedQty(coupon.getId());
        } catch (Exception e) {
            log.error("秒杀券 issued_qty 同步失败（非关键）: couponId={}", coupon.getId(), e);
        }
        return userCouponViewAssembler.assemble(uc, coupon);
    }

    /**
     * 支付成功后发券（付费券购买→支付→回调发券）。
     *
     * <p>直接创建 status=未使用的 user_coupon；由 pay-service 支付成功回调调用。
     */
    @Transactional(rollbackFor = Exception.class)
    public void grantAfterPayment(Long userId, String couponId) {
        Coupon coupon = findByCouponId(couponId);
        if (!coupon.isActive()) {
            throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "优惠券已失效");
        }
        if (userCouponRepository.countByUserIdAndCouponId(userId, coupon.getId()) > 0) {
            throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "已发放过该优惠券");
        }
        insertUserCoupon(userId, coupon.getId());
    }

    // ==================== 内部方法 ====================

    /** 按业务唯一键取券（不存在抛 COUPON_NOT_FOUND）。 */
    private Coupon findByCouponId(String couponId) {
        Coupon coupon = couponRepository.findByCouponId(couponId);
        if (coupon == null) {
            throw new BizException(ErrorCode.COUPON_NOT_FOUND);
        }
        return coupon;
    }

    /** 取券并校验「可领取」：已发布 + 在领取时间窗内。 */
    private Coupon requireClaimableCoupon(String couponId) {
        Coupon coupon = findByCouponId(couponId);
        if (CouponStatusEnum.ACTIVE.getCode() != coupon.getStatus()) {
            throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "优惠券已失效");
        }
        LocalDateTime now = LocalDateTime.now();
        if (coupon.getStartTime() != null && now.isBefore(coupon.getStartTime())) {
            throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "优惠券未到领取时间");
        }
        if (coupon.getEndTime() != null && now.isAfter(coupon.getEndTime())) {
            throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "优惠券已过期");
        }
        return coupon;
    }

    /** 写入一张可用券（status=未使用）。 */
    private UserCoupon insertUserCoupon(Long userId, Long couponId) {
        return userCouponRepository.save(UserCoupon.issue(userId, couponId));
    }
}
