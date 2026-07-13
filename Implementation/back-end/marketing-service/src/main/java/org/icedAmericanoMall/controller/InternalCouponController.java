package org.icedAmericanoMall.controller;

import lombok.RequiredArgsConstructor;
import org.icedAmericanoMall.service.CouponService;
import org.springframework.web.bind.annotation.*;

/**
 * 优惠券内部接口 —— 供 trade-service 下单时抵扣/回滚（Feign 调用）。
 * {@code /internal/**} 路径：异常直接透传，不包 Result。
 */
@RestController
@RequestMapping("/internal/coupon")
@RequiredArgsConstructor
public class InternalCouponController {

    private final CouponService couponService;

    /** 使用优惠券，返回实际抵扣金额（分）。 */
    @PostMapping("/use")
    public int use(@RequestParam Long userId, @RequestParam Long userCouponId,
                   @RequestParam String orderNo, @RequestParam Integer orderAmount) {
        return couponService.useCoupon(userId, userCouponId, orderNo, orderAmount);
    }

    /** 按订单号回滚优惠券。 */
    @PostMapping("/rollback")
    public void rollback(@RequestParam String orderNo) {
        couponService.rollbackByOrderNo(orderNo);
    }
}
