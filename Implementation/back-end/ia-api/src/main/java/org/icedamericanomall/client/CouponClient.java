package org.icedamericanomall.client;

import org.icedamericanomall.fallback.CouponClientFallback;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Feign client for marketing-service internal coupon endpoints.
 * Used by trade-service to apply / roll back coupon discounts at order time.
 */
@FeignClient(name = "marketing-service", path = "/internal/coupon", contextId = "coupon",
        fallbackFactory = CouponClientFallback.class)
public interface CouponClient {

    /** V4.2: 使用优惠券，返回实际抵扣金额（分）。新增 orderType+sellerId 用于类别/店铺校验。 */
    @PostMapping("/use")
    Integer useCoupon(@RequestParam Long userId, @RequestParam Long userCouponId,
                      @RequestParam String orderNo, @RequestParam Integer orderAmount,
                      @RequestParam Integer orderType, @RequestParam Long sellerId);

    /** 按订单号回滚优惠券（取消/超时/下单失败补偿）。 */
    @PostMapping("/rollback")
    void rollbackByOrderNo(@RequestParam String orderNo);
}
