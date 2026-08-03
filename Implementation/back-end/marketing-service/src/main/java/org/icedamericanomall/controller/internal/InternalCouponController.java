package org.icedamericanomall.controller.internal;

import lombok.RequiredArgsConstructor;
import org.icedamericanomall.service.CouponService;
import org.springframework.web.bind.annotation.*;

/**
 * 优惠券内部接口 —— 供 trade-service 下单时抵扣/回滚/发券（Feign 调用）。
 * {@code /internal/**} 路径：异常直接透传，不包 Result。
 */
@RestController
@RequestMapping("/internal/coupon")
@RequiredArgsConstructor
public class InternalCouponController {

    private final CouponService couponService;

    /** V4.2: 使用优惠券，返回实际抵扣金额（分）。新增 orderType+sellerId 用于类别/店铺校验。 */
    @PostMapping("/use")
    public int use(@RequestParam Long userId, @RequestParam Long userCouponId,
                   @RequestParam String orderNo, @RequestParam Integer orderAmount,
                   @RequestParam Integer orderType, @RequestParam Long sellerId) {
        return couponService.useCoupon(userId, userCouponId, orderNo, orderAmount, orderType, sellerId);
    }

    /** 按订单号回滚优惠券。 */
    @PostMapping("/rollback")
    public void rollback(@RequestParam String orderNo) {
        couponService.rollbackByOrderNo(orderNo);
    }

    /**
     * V4.4: 支付成功后发券（京东标准：付费券购买→支付→回调发券）。
     * 由 pay-service 支付成功回调调用，直接创建 status=1 的 user_coupon。
     */
    @PostMapping("/grant")
    public void grant(@RequestParam Long userId, @RequestParam String couponId) {
        couponService.grantAfterPayment(userId, couponId);
    }
}
