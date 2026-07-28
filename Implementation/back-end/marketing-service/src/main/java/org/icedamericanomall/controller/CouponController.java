package org.icedamericanomall.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.icedamericanomall.domain.entity.CouponEntity;
import org.icedamericanomall.domain.entity.UserCouponEntity;
import org.icedamericanomall.service.CouponService;
import org.noLazy.common.domain.Result;
import org.noLazy.common.utils.UserContext;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 用户端优惠券接口。
 * V4.0: 新增付费购买 + 秒杀级领取端点（京东标准多维度优惠券模型）。
 */
@RestController
@RequestMapping("/api/coupon")
@RequiredArgsConstructor
public class CouponController {

    private final CouponService couponService;

    /** 免费领取优惠券（普通/不限量/付费PRE_PAID升级） */
    @PostMapping("/claim")
    public Result<UserCouponEntity> claim(@RequestParam String couponId) {
        return Result.ok(couponService.claim(UserContext.getUserId(), couponId));
    }

    /** V4.0: 秒杀级优惠券领取（Redis Lua 高并发） */
    @PostMapping("/grab")
    public Result<UserCouponEntity> grab(@RequestParam String couponId) {
        return Result.ok(couponService.claimWithGrab(UserContext.getUserId(), couponId));
    }

    /** V4.0: 购买付费优惠券（创建 PRE_PAID 记录） */
    @PostMapping("/purchase")
    public Result<UserCouponEntity> purchase(@RequestParam String couponId) {
        return Result.ok(couponService.purchaseCoupon(UserContext.getUserId(), couponId));
    }

    /** 可用优惠券列表 */
    @GetMapping("/available")
    public Result<List<UserCouponEntity>> available() {
        return Result.ok(couponService.getUserAvailableCoupons(UserContext.getUserId()));
    }

    /** 已使用/已过期 */
    @GetMapping("/used")
    public Result<List<UserCouponEntity>> used() {
        return Result.ok(couponService.getUserUsedCoupons(UserContext.getUserId()));
    }

    /** 可用优惠券模板列表 */
    @GetMapping("/template")
    public Result<List<CouponEntity>> templates() {
        return Result.ok(couponService.lambdaQuery()
                .eq(CouponEntity::getStatus, 1)
                .gt(CouponEntity::getEndTime, java.time.LocalDateTime.now())
                .list());
    }
}
