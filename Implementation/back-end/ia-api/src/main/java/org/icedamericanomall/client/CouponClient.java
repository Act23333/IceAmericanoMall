package org.icedamericanomall.client;

import org.icedamericanomall.dto.CouponStackInfoDTO;
import org.icedamericanomall.fallback.CouponClientFallback;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * Feign client for marketing-service internal coupon endpoints.
 * Used by trade-service to apply / roll back / stack-validate coupon discounts at order time.
 */
@FeignClient(name = "marketing-service", path = "/internal/coupon", contextId = "coupon",
        fallbackFactory = CouponClientFallback.class)
public interface CouponClient {

    /** V4.3: 使用优惠券，返回实际抵扣金额（分）。productIds+categoryIds 用于 scope 校验 */
    @PostMapping("/use")
    Integer useCoupon(@RequestParam Long userId, @RequestParam Long userCouponId,
                      @RequestParam String orderNo, @RequestParam Integer orderAmount,
                      @RequestParam Integer orderType, @RequestParam Long sellerId,
                      @RequestParam(required = false) String productIds,
                      @RequestParam(required = false) String categoryIds);

    /** 按订单号回滚优惠券（取消/超时/下单失败补偿）。 */
    @PostMapping("/rollback")
    void rollbackByOrderNo(@RequestParam String orderNo);

    /** V4.3: 批量查询券模板（下单时校验多券叠加规则）。 */
    @GetMapping("/batch")
    List<CouponStackInfoDTO> batchGet(@RequestParam List<Long> ids);
}
