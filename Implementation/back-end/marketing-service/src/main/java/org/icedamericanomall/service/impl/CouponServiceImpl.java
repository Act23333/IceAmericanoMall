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
     * V4.0: 多维度领取优惠券。
     * - UNLIMITED 券: 不检查总量上限
     * - PAID_PURCHASE 券: 需先购买(PRE_PAID记录)，此方法仅升级为未使用
     * - NEED_GRAB 券: 本方法拒绝，请使用 claimWithGrab()
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserCouponEntity claim(Long userId, String couponId) {
        CouponEntity coupon = lambdaQuery().eq(CouponEntity::getCouponId, couponId).one();
        if (coupon == null) throw new BizException(ErrorCode.USER_NOT_FOUND, "优惠券不存在");
        if (coupon.getStatus() != 1) throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "优惠券已失效");
        if (LocalDateTime.now().isAfter(coupon.getEndTime()))
            throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "优惠券已过期");

        // V4.0: 秒杀券走 Redis 高并发通道
        if (coupon.getGrabType() != null && coupon.getGrabType() == GrabTypeEnum.NEED_GRAB.getCode()) {
            throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION,
                    "该优惠券为秒杀券，请使用 /api/coupon/grab 接口领取");
        }

        // V4.0: 检查重复领取
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

        // V4.0: 付费券：仅允许已购买的 PRE_PAID 记录转为未使用
        if (coupon.getGrantType() != null && coupon.getGrantType() == GrantTypeEnum.PAID_PURCHASE.getCode()) {
            UserCouponEntity prePaid = userCouponMapper.selectOne(
                    new LambdaQueryWrapper<UserCouponEntity>()
                            .eq(UserCouponEntity::getUserId, userId)
                            .eq(UserCouponEntity::getCouponId, coupon.getId())
                            .eq(UserCouponEntity::getStatus, 4)); // PRE_PAID
            if (prePaid == null)
                throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "请先购买该优惠券");
            prePaid.setStatus(1);
            userCouponMapper.updateById(prePaid);
            return prePaid;
        }

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
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int useCoupon(Long userId, Long userCouponId, String orderNo, int orderAmount) {
        UserCouponEntity uc = userCouponMapper.selectById(userCouponId);
        if (uc == null || !uc.getUserId().equals(userId))
            throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "优惠券不存在");
        if (uc.getStatus() != 1)
            throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "优惠券不可用");

        CouponEntity coupon = getById(uc.getCouponId());
        if (coupon == null) throw new BizException(ErrorCode.USER_NOT_FOUND, "优惠券模板已删除");
        if (orderAmount < coupon.getMinAmount())
            throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "未达到最低消费金额");

        // V4.0: 优先使用 discountType，兼容旧 type 字段
        int dt = coupon.getDiscountType() != null ? coupon.getDiscountType() : coupon.getType();
        int discount;
        if (dt == DiscountTypeEnum.FIXED.getCode()) {
            // 满减券：固定金额抵扣
            discount = coupon.getValue();
        } else if (dt == DiscountTypeEnum.PERCENTAGE.getCode()) {
            // 折扣券: value=85 表示 8.5折 → 抵扣 15%
            discount = orderAmount * (100 - coupon.getValue()) / 100;
        } else if (dt == DiscountTypeEnum.CASH_COUPON.getCode()) {
            // V4.0: 代金券：面额抵扣，不超过订单总金额
            discount = Math.min(coupon.getValue(), orderAmount);
        } else {
            throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "未知的优惠券类型");
        }
        discount = Math.min(discount, orderAmount); // 抵扣金额不超过订单总金额

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

    // === V4.0: 多维度优惠券模型新增方法 ===

    /**
     * V4.0: 购买付费优惠券。
     * 创建 PRE_PAID(status=4) 状态的 user_coupon 记录，
     * 用户后续调用 claim() 时自动升级为 status=1。
     * TODO V4.1: 集成支付服务扣除余额/发起支付。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserCouponEntity purchaseCoupon(Long userId, String couponId) {
        CouponEntity coupon = lambdaQuery().eq(CouponEntity::getCouponId, couponId).one();
        if (coupon == null) throw new BizException(ErrorCode.USER_NOT_FOUND, "优惠券不存在");
        if (coupon.getGrantType() == null || coupon.getGrantType() != GrantTypeEnum.PAID_PURCHASE.getCode())
            throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "该优惠券无需购买，可直接领取");
        if (coupon.getPriceInCents() == null || coupon.getPriceInCents() <= 0)
            throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "优惠券价格未配置");

        // 检查是否已购买/领取
        Long exists = userCouponMapper.selectCount(
                new LambdaQueryWrapper<UserCouponEntity>()
                        .eq(UserCouponEntity::getUserId, userId)
                        .eq(UserCouponEntity::getCouponId, coupon.getId()));
        if (exists > 0) throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "已购买或领取过该优惠券");

        // TODO V4.1: 调用支付服务扣款 balanceClient.deduct(userId, coupon.getPriceInCents())

        UserCouponEntity uc = new UserCouponEntity();
        uc.setUserId(userId);
        uc.setCouponId(coupon.getId());
        uc.setStatus(4); // PRE_PAID
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
