package org.icedAmericanoMall.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.icedAmericanoMall.domain.entity.CouponEntity;
import org.icedAmericanoMall.domain.entity.UserCouponEntity;
import org.icedAmericanoMall.mapper.CouponMapper;
import org.icedAmericanoMall.mapper.UserCouponMapper;
import org.icedAmericanoMall.service.CouponService;
import org.noLazy.common.enums.ErrorCode;
import org.noLazy.common.exception.BizException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class CouponServiceImpl extends ServiceImpl<CouponMapper, CouponEntity> implements CouponService {

    private final UserCouponMapper userCouponMapper;

    public CouponServiceImpl(UserCouponMapper userCouponMapper) {
        this.userCouponMapper = userCouponMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserCouponEntity claim(Long userId, String couponId) {
        CouponEntity coupon = lambdaQuery().eq(CouponEntity::getCouponId, couponId).one();
        if (coupon == null) throw new BizException(ErrorCode.USER_NOT_FOUND, "优惠券不存在");
        if (coupon.getStatus() != 1) throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "优惠券已失效");
        if (coupon.getIssuedQty() >= coupon.getTotalQty())
            throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "优惠券已领完");
        if (LocalDateTime.now().isAfter(coupon.getEndTime()))
            throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "优惠券已过期");

        // Check duplicate claim
        Long count = userCouponMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UserCouponEntity>()
                        .eq(UserCouponEntity::getUserId, userId)
                        .eq(UserCouponEntity::getCouponId, coupon.getId()));
        if (count > 0) throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "已领取过该优惠券");

        // Increment issued count
        lambdaUpdate().eq(CouponEntity::getId, coupon.getId())
                .setSql("issued_qty = issued_qty + 1").update();

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
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UserCouponEntity>()
                        .eq(UserCouponEntity::getUserId, userId)
                        .eq(UserCouponEntity::getStatus, 1));
    }

    @Override
    public List<UserCouponEntity> getUserUsedCoupons(Long userId) {
        return userCouponMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UserCouponEntity>()
                        .eq(UserCouponEntity::getUserId, userId)
                        .ne(UserCouponEntity::getStatus, 1)
                        .orderByDesc(UserCouponEntity::getUseTime));
    }

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

        int discount = 0;
        if (coupon.getType() == 1) {
            // 满减
            discount = coupon.getValue();
        } else if (coupon.getType() == 2) {
            // 折扣: value=85 表示 8.5折
            discount = orderAmount * (100 - coupon.getValue()) / 100;
        }
        discount = Math.min(discount, orderAmount); // cap at order total

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
}
