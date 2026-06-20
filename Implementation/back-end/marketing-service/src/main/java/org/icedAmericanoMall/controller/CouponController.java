package org.icedAmericanoMall.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.icedAmericanoMall.domain.entity.CouponEntity;
import org.icedAmericanoMall.domain.entity.UserCouponEntity;
import org.icedAmericanoMall.service.CouponService;
import org.noLazy.common.domain.Result;
import org.noLazy.common.utils.UserContext;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 用户端优惠券接口。
 */
@RestController
@RequestMapping("/api/coupon")
@RequiredArgsConstructor
public class CouponController {

    private final CouponService couponService;

    /** 领取优惠券 */
    @PostMapping("/claim")
    public Result<UserCouponEntity> claim(@RequestParam String couponId) {
        return Result.ok(couponService.claim(UserContext.getUser(), couponId));
    }

    /** 可用优惠券列表 */
    @GetMapping("/available")
    public Result<List<UserCouponEntity>> available() {
        return Result.ok(couponService.getUserAvailableCoupons(UserContext.getUser()));
    }

    /** 已使用/已过期 */
    @GetMapping("/used")
    public Result<List<UserCouponEntity>> used() {
        return Result.ok(couponService.getUserUsedCoupons(UserContext.getUser()));
    }

    /** 可用优惠券列表（分页） */
    @GetMapping("/template")
    public Result<List<CouponEntity>> templates() {
        return Result.ok(couponService.lambdaQuery()
                .eq(CouponEntity::getStatus, 1)
                .gt(CouponEntity::getEndTime, java.time.LocalDateTime.now())
                .list());
    }
}
