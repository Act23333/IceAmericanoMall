package org.icedamericanomall.controller;

import lombok.RequiredArgsConstructor;
import org.icedamericanomall.domain.dto.CouponAvailableFilterReq;
import org.icedamericanomall.domain.entity.CouponEntity;
import org.icedamericanomall.domain.entity.UserCouponEntity;
import org.icedamericanomall.domain.vo.CouponAvailableVO;
import org.icedamericanomall.service.CouponService;
import org.noLazy.common.domain.Result;
import org.noLazy.common.utils.UserContext;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 用户端优惠券接口。
 * V4.3: 新增预过滤端点（京东标准：结算页只返回可用的券）。
 */
@RestController
@RequestMapping("/api/coupon")
@RequiredArgsConstructor
public class CouponController {

    private final CouponService couponService;

    /** 免费领取优惠券 */
    @PostMapping("/claim")
    public Result<UserCouponEntity> claim(@RequestParam String couponId) {
        return Result.ok(couponService.claim(UserContext.getUserId(), couponId));
    }

    /** 秒杀级优惠券领取（Redis Lua 高并发） */
    @PostMapping("/grab")
    public Result<UserCouponEntity> grab(@RequestParam String couponId) {
        return Result.ok(couponService.claimWithGrab(UserContext.getUserId(), couponId));
    }

    /** 购买付费优惠券 */
    @PostMapping("/purchase")
    public Result<UserCouponEntity> purchase(@RequestParam String couponId) {
        return Result.ok(couponService.purchaseCoupon(UserContext.getUserId(), couponId));
    }

    /** 所有未使用券列表（无过滤，向后兼容） */
    @GetMapping("/available")
    public Result<List<UserCouponEntity>> available() {
        return Result.ok(couponService.getUserAvailableCoupons(UserContext.getUserId()));
    }

    /** V4.3: 结算页预过滤——根据订单上下文返回适用/不适用的券列表（京东标准） */
    @PostMapping("/available/filter")
    public Result<List<CouponAvailableVO>> availableFiltered(@RequestBody CouponAvailableFilterReq filter) {
        return Result.ok(couponService.getAvailableCoupons(UserContext.getUserId(), filter));
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
