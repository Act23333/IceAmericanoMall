package org.icedamericanomall.controller;

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
 * V4.4: 统一领取入口 smartClaim（自动路由 DB/Redis）；删除 purchase（付费券走 trade 订单+支付）。
 */
@RestController
@RequestMapping("/api/coupon")
@RequiredArgsConstructor
public class CouponController {

    private final CouponService couponService;

    /** V4.4: 智能领取——根据 grabType 自动路由 DB 或 Redis Lua 通道（京东标准） */
    @PostMapping("/claim")
    public Result<UserCouponEntity> claim(@RequestParam String couponId) {
        return Result.ok(couponService.smartClaim(UserContext.getUserId(), couponId));
    }

    /** 秒杀级优惠券领取（直接走 Redis Lua 高并发通道，兼容保留） */
    @PostMapping("/grab")
    public Result<UserCouponEntity> grab(@RequestParam String couponId) {
        return Result.ok(couponService.claimWithGrab(UserContext.getUserId(), couponId));
    }

    /** 所有未使用券列表（向后兼容） */
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
