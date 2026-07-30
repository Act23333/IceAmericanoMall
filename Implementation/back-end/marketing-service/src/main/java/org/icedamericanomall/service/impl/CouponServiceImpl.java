package org.icedamericanomall.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.constants.*;
import org.icedamericanomall.domain.entity.CouponEntity;
import org.icedamericanomall.domain.entity.UserCouponEntity;
import org.icedamericanomall.mapper.CouponMapper;
import org.icedamericanomall.mapper.UserCouponMapper;
import org.icedamericanomall.service.CouponService;
import org.noLazy.common.enums.ErrorCode;
import org.noLazy.common.exception.BizException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * V4.0: 多维度优惠券服务实现（京东标准）。
 * <p>
 * 优惠券5维模型: discountType × couponCategory × grantType × stockType × grabType
 * 支持: 无限量/付费购买/秒杀级领取(Redis Lua)/免费领取。
 */
@Slf4j
@Service
public class CouponServiceImpl extends ServiceImpl<CouponMapper, CouponEntity> implements CouponService {

    private final UserCouponMapper userCouponMapper;
    private final CouponGrabLuaScript couponGrabLuaScript;

    public CouponServiceImpl(UserCouponMapper userCouponMapper, CouponGrabLuaScript couponGrabLuaScript) {
        this.userCouponMapper = userCouponMapper;
        this.couponGrabLuaScript = couponGrabLuaScript;
    }

    /**
     * V4.4: 普通领取优惠券（免费券、不限量券）。
     * NEED_GRAB 券由 smartClaim 自动路由到 claimWithGrab。
     * PAID_PURCHASE 券走 trade 订单→支付→grantAfterPayment 通道，不再走此处。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserCouponEntity claim(Long userId, String couponId) {
        CouponEntity coupon = lambdaQuery().eq(CouponEntity::getCouponId, couponId).one();
        if (coupon == null) throw new BizException(ErrorCode.USER_NOT_FOUND, "优惠券不存在");
        if (coupon.getStatus() != 1) throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "优惠券已失效");
        // V4.4: 增加 startTime 校验（京东标准：到点才能领）
        if (coupon.getStartTime() != null && LocalDateTime.now().isBefore(coupon.getStartTime()))
            throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "优惠券未到领取时间");
        if (LocalDateTime.now().isAfter(coupon.getEndTime()))
            throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "优惠券已过期");

        // 检查重复领取
        Long count = userCouponMapper.selectCount(
                new LambdaQueryWrapper<UserCouponEntity>()
                        .eq(UserCouponEntity::getUserId, userId)
                        .eq(UserCouponEntity::getCouponId, coupon.getId()));
        if (count > 0) throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "已领取过该优惠券");

        // V4.0: 限量券：原子条件 UPDATE 扣减总库存
        if (coupon.getStockType() == null || coupon.getStockType() == CouponStockTypeEnum.LIMITED.getCode()) {
            boolean incremented = lambdaUpdate().eq(CouponEntity::getId, coupon.getId())
                    .lt(CouponEntity::getIssuedQty, coupon.getTotalQty())
                    .setSql("issued_qty = issued_qty + 1").update();
            if (!incremented) throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "优惠券已领完");
        }
        // V4.0: UNLIMITED 券：不检查总量上限，跳过 issuedQty 扣减

        UserCouponEntity uc = new UserCouponEntity();
        uc.setUserId(userId);
        uc.setCouponId(coupon.getId());
        uc.setStatus(1);
        userCouponMapper.insert(uc);
        return uc;
    }

    @Override
    public List<UserCouponEntity> getUserAvailableCoupons(Long userId) {
        return userCouponMapper.selectList(
                new LambdaQueryWrapper<UserCouponEntity>()
                        .eq(UserCouponEntity::getUserId, userId)
                        .eq(UserCouponEntity::getStatus, 1));
    }

    @Override
    public List<UserCouponEntity> getUserUsedCoupons(Long userId) {
        return userCouponMapper.selectList(
                new LambdaQueryWrapper<UserCouponEntity>()
                        .eq(UserCouponEntity::getUserId, userId)
                        .ne(UserCouponEntity::getStatus, 1)
                        .orderByDesc(UserCouponEntity::getUseTime));
    }

    /**
     * V4.0: 使用优惠券（下单时调用），支持 discountType 三维度。
     * 向后兼容: discountType 为空时回退到旧 type 字段。
     */
    /**
     * V4.2: 使用优惠券 — 增加 couponCategory 与订单类型/卖家匹配校验（京东标准）。
     *
     * <pre>
     * 优惠券使用规则:
     * - PLATFORM(1): 所有订单类型通用，不限卖家
     * - SHOP(2):     仅限该店铺(sellerId)的订单
     * - FLASH_SALE(3): 仅限秒杀订单(orderType=3)
     * - EXCLUSIVE(4): 平台独占券，仅限普通/立即购买订单（不含秒杀）
     * - 秒杀订单(orderType=3): 拒绝所有优惠券（秒杀价已是底价，后端兜底）
     * </pre>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int useCoupon(Long userId, Long userCouponId, String orderNo, int orderAmount,
                          Integer orderType, Long sellerId) {
        UserCouponEntity uc = userCouponMapper.selectById(userCouponId);
        if (uc == null || !uc.getUserId().equals(userId))
            throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "优惠券不存在");
        if (uc.getStatus() != 1)
            throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "优惠券不可用");

        CouponEntity coupon = getById(uc.getCouponId());
        if (coupon == null) throw new BizException(ErrorCode.USER_NOT_FOUND, "优惠券模板已删除");
        if (orderAmount < coupon.getMinAmount())
            throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "未达到最低消费金额");

        // V4.2: couponCategory 与订单类型/卖家校验（京东标准：券类别决定适用范围）
        int category = coupon.getCouponCategory() != null ? coupon.getCouponCategory()
                : (coupon.getSellerId() == null ? CouponCategoryEnum.PLATFORM.getCode()
                                                 : CouponCategoryEnum.SHOP.getCode());
        if (orderType != null) {
            // 秒杀订单不允许使用任何优惠券（后端兜底，前端也应限制）
            if (orderType == 3) { // FLASH_SALE
                throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "秒杀订单不支持使用优惠券");
            }
            // FLASH_SALE 券仅限秒杀订单 → 但秒杀订单已在上方拒绝，此处为语义完整性
            if (category == CouponCategoryEnum.FLASH_SALE.getCode() && orderType != 3) {
                throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION,
                        "秒杀券仅限秒杀订单使用");
            }
        }
        // SHOP 券：卖家必须匹配
        if (category == CouponCategoryEnum.SHOP.getCode()) {
            if (coupon.getSellerId() == null || !coupon.getSellerId().equals(sellerId)) {
                throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION,
                        "店铺券仅限该店铺订单使用");
            }
        }

        // V4.0: 优先使用 discountType，兼容旧 type 字段
        int dt = coupon.getDiscountType() != null ? coupon.getDiscountType() : coupon.getType();
        int discount;
        if (dt == DiscountTypeEnum.FIXED.getCode()) {
            discount = coupon.getValue();
        } else if (dt == DiscountTypeEnum.PERCENTAGE.getCode()) {
            discount = orderAmount * (100 - coupon.getValue()) / 100;
        } else if (dt == DiscountTypeEnum.CASH_COUPON.getCode()) {
            discount = Math.min(coupon.getValue(), orderAmount);
        } else {
            throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "未知的优惠券类型");
        }
        discount = Math.min(discount, orderAmount);

        uc.setStatus(2);
        uc.setUsedOrderNo(orderNo);
        uc.setUseTime(LocalDateTime.now());
        userCouponMapper.updateById(uc);

        return discount;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rollbackCoupon(Long userCouponId) {
        UserCouponEntity uc = userCouponMapper.selectById(userCouponId);
        if (uc != null && uc.getStatus() == 2) {
            uc.setStatus(1);
            uc.setUsedOrderNo(null);
            uc.setUseTime(null);
            userCouponMapper.updateById(uc);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rollbackByOrderNo(String orderNo) {
        UserCouponEntity uc = userCouponMapper.selectOne(
                new LambdaQueryWrapper<UserCouponEntity>()
                        .eq(UserCouponEntity::getUsedOrderNo, orderNo)
                        .eq(UserCouponEntity::getStatus, 2));
        if (uc != null) {
            uc.setStatus(1);
            uc.setUsedOrderNo(null);
            uc.setUseTime(null);
            userCouponMapper.updateById(uc);
        }
    }

    // === V4.4: smartClaim + grantAfterPayment ===

    /**
     * V4.4: 智能领取——根据 grabType 自动路由到 DB 或 Redis Lua 通道。
     * 替代了之前分离的 /claim 和 /grab 端点，前端统一调此方法。
     */
    @Override
    public UserCouponEntity smartClaim(Long userId, String couponId) {
        CouponEntity coupon = lambdaQuery().eq(CouponEntity::getCouponId, couponId).one();
        if (coupon == null) throw new BizException(ErrorCode.USER_NOT_FOUND, "优惠券不存在");
        if (coupon.getGrabType() != null && coupon.getGrabType() == GrabTypeEnum.NEED_GRAB.getCode()) {
            return claimWithGrab(userId, couponId);
        }
        return claim(userId, couponId);
    }

    /**
     * V4.4: 支付成功后发券（京东标准：付费券购买→支付→回调发券）。
     * 直接创建 status=1 的 user_coupon，跳过 PRE_PAID 状态。
     * 由 pay-service 支付成功回调 → InternalCouponController.grant 端点调用。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserCouponEntity grantAfterPayment(Long userId, String couponId) {
        CouponEntity coupon = lambdaQuery().eq(CouponEntity::getCouponId, couponId).one();
        if (coupon == null) throw new BizException(ErrorCode.USER_NOT_FOUND, "优惠券不存在");
        if (coupon.getStatus() != 1) throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "优惠券已失效");

        // 防重：检查是否已发过
        Long exists = userCouponMapper.selectCount(
                new LambdaQueryWrapper<UserCouponEntity>()
                        .eq(UserCouponEntity::getUserId, userId)
                        .eq(UserCouponEntity::getCouponId, coupon.getId()));
        if (exists > 0) throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "已发放过该优惠券");

        UserCouponEntity uc = new UserCouponEntity();
        uc.setUserId(userId);
        uc.setCouponId(coupon.getId());
        uc.setStatus(1);
        userCouponMapper.insert(uc);
        return uc;
    }

    /**
     * V4.0: 秒杀级优惠券领取（Redis Lua 原子操作 + DB 落库）。
     * 仅 grabType=NEED_GRAB 的优惠券走此通道。
     */
    @Override
    public UserCouponEntity claimWithGrab(Long userId, String couponId) {
        CouponEntity coupon = lambdaQuery().eq(CouponEntity::getCouponId, couponId).one();
        if (coupon == null) throw new BizException(ErrorCode.USER_NOT_FOUND, "优惠券不存在");
        if (coupon.getStatus() != 1) throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "优惠券已失效");
        if (coupon.getStartTime() != null && LocalDateTime.now().isBefore(coupon.getStartTime()))
            throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "优惠券未到领取时间");
        if (LocalDateTime.now().isAfter(coupon.getEndTime()))
            throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "优惠券已过期");
        if (coupon.getGrabType() == null || coupon.getGrabType() != GrabTypeEnum.NEED_GRAB.getCode())
            throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "该优惠券无需抢，请使用普通领取接口");

        // Redis Lua 原子预扣
        long result = couponGrabLuaScript.tryClaim(coupon.getId(), userId);
        if (result == -1) throw new BizException(ErrorCode.FLASH_SALE_SOLD_OUT, "优惠券已抢光");
        if (result == -2) throw new BizException(ErrorCode.FLASH_SALE_LIMIT_EXCEEDED, "已领取过该优惠券");

        // Redis 扣减成功 → DB 落库
        UserCouponEntity uc = new UserCouponEntity();
        uc.setUserId(userId);
        uc.setCouponId(coupon.getId());
        uc.setStatus(1);
        userCouponMapper.insert(uc);

        // 同步更新 MySQL 的 issued_qty（非关键路径，失败不影响领取）
        try {
            lambdaUpdate().eq(CouponEntity::getId, coupon.getId())
                    .setIncrBy(CouponEntity::getIssuedQty, 1).update();
        } catch (Exception e) {
            log.error("秒杀券 issued_qty 同步失败（非关键）: couponId={}", coupon.getId(), e);
        }

        return uc;
    }
}
