package org.icedamericanomall.marketing.adapter.web;

import lombok.RequiredArgsConstructor;
import org.icedamericanomall.common.api.Result;
import org.icedamericanomall.common.security.UserContext;
import org.icedamericanomall.marketing.adapter.response.CouponVO;
import org.icedamericanomall.marketing.adapter.response.UserCouponVO;
import org.icedamericanomall.marketing.app.coupon.CouponAdminManager;
import org.icedamericanomall.marketing.app.coupon.CouponClaimManager;
import org.icedamericanomall.marketing.app.coupon.CouponUsageManager;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 用户端优惠券接口。
 *
 * <p>V4.4 统一领取入口 {@code smartClaim}（自动路由 DB/Redis）；付费券走 trade 订单 + 支付通道。
 * 出参一律为 VO（聚合不泄漏到适配层之外）。
 *
 * <p><b>归位说明</b>：adapter 子包按<b>受众</b>分组（web/admin/internal），不按业务功能分组——
 * 故优惠券/秒杀/积分/签到/商家端入口全部收敛在 {@code adapter/web}，
 * 管理后台在 {@code adapter/admin}，Feign 内部接口在 {@code adapter/internal}。
 */
@RestController
@RequestMapping("/api/coupon")
@RequiredArgsConstructor
public class CouponController {

    private final CouponClaimManager couponClaimManager;
    private final CouponUsageManager couponUsageManager;
    private final CouponAdminManager couponAdminManager;

    /** V4.4: 智能领取——根据 grabType 自动路由 DB 或 Redis Lua 通道（京东标准）。 */
    @PostMapping("/claim")
    public Result<UserCouponVO> claim(@RequestParam String couponId) {
        return Result.ok(couponClaimManager.smartClaim(UserContext.getUserId(), couponId));
    }

    /** 秒杀级优惠券领取（直接走 Redis Lua 高并发通道，兼容保留）。 */
    @PostMapping("/grab")
    public Result<UserCouponVO> grab(@RequestParam String couponId) {
        return Result.ok(couponClaimManager.claimWithGrab(UserContext.getUserId(), couponId));
    }

    /** 所有未使用券列表（向后兼容）。 */
    @GetMapping("/available")
    public Result<List<UserCouponVO>> available() {
        return Result.ok(couponUsageManager.listAvailableCoupons(UserContext.getUserId()));
    }

    /** 已使用/已过期优惠券列表。 */
    @GetMapping("/used")
    public Result<List<UserCouponVO>> used() {
        return Result.ok(couponUsageManager.listUsedCoupons(UserContext.getUserId()));
    }

    /** 可领取的优惠券模板列表（已发布且未过期）。 */
    @GetMapping("/template")
    public Result<List<CouponVO>> templates() {
        return Result.ok(couponAdminManager.listActiveTemplates());
    }

    /** 我的券三态计数（M7：available/used/expired，V5.11）。 */
    @GetMapping("/count")
    public Result<Map<String, Integer>> count() {
        return Result.ok(couponUsageManager.countMyCoupons(UserContext.getUserId()));
    }
}
