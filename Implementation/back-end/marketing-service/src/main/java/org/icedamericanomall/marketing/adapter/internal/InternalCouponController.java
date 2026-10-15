package org.icedamericanomall.marketing.adapter.internal;

import lombok.RequiredArgsConstructor;
import org.icedamericanomall.dto.CouponStackInfoCO;
import org.icedamericanomall.marketing.app.coupon.CouponClaimManager;
import org.icedamericanomall.marketing.app.coupon.CouponUsageManager;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 优惠券内部接口 —— 供 trade-service 下单时抵扣/回滚/发券/叠加校验（Feign 调用）。
 * {@code /internal/**} 路径：异常直接透传，不包 Result。
 */
@RestController
@RequestMapping("/internal/coupon")
@RequiredArgsConstructor
public class InternalCouponController {

    private final CouponUsageManager couponUsageManager;
    private final CouponClaimManager couponClaimManager;

    /**
     * V4.2: 使用优惠券，返回实际抵扣金额（分）。orderType+sellerId 用于类别/店铺校验。
     *
     * <p>V4.3 起 {@code productIds}/{@code categoryIds}（均逗号分隔、均可缺省）用于
     * {@code scope_type/scope_values} 适用范围校验；缺省时若该券限定了品类/单品则拒绝核销
     * （详见 {@code CouponUsageManager.useCoupon}）。
     */
    @PostMapping("/use")
    public int use(@RequestParam(name = "user_id") Long userId, @RequestParam(name = "user_coupon_id") Long userCouponId,
                   @RequestParam(name = "order_no") String orderNo, @RequestParam(name = "order_amount") Integer orderAmount,
                   @RequestParam(name = "order_type") Integer orderType, @RequestParam(name = "seller_id") Long sellerId,
                   @RequestParam(name = "product_ids", required = false) String productIds,
                   @RequestParam(name = "category_ids", required = false) String categoryIds) {
        return couponUsageManager.useCoupon(userId, userCouponId, orderNo, orderAmount,
                orderType, sellerId, productIds, categoryIds);
    }

    /** 按订单号回滚优惠券。 */
    @PostMapping("/rollback")
    public void rollback(@RequestParam(name = "order_no") String orderNo) {
        couponUsageManager.rollbackByOrderNo(orderNo);
    }

    /**
     * V4.4: 支付成功后发券（京东标准：付费券购买→支付→回调发券）。
     * 由 pay-service 支付成功回调调用，直接创建 status=1 的 user_coupon。
     */
    @PostMapping("/grant")
    public void grant(@RequestParam(name = "user_id") Long userId, @RequestParam(name = "coupon_id") String couponId) {
        couponClaimManager.grantAfterPayment(userId, couponId);
    }

    /**
     * V4.3: 批量查询持券的叠加信息（I46），供下调方校验多券叠加规则。
     *
     * <p>入参 {@code ids} 为 <b>{@code user_coupon.id}</b> 列表（与 {@code /use} 的
     * {@code userCouponId} 同口径）；Spring 按逗号切分绑定（Feign 亦以逗号分隔传输）。
     * 券不存在时跳过（fail-open），返回值不包 {@code Result}。
     *
     * @param ids 持券 ID 列表
     * @return 叠加信息列表（{@code id}=持券 ID，{@code couponId}=券业务标识）
     */
    @GetMapping("/batch")
    public List<CouponStackInfoCO> batch(@RequestParam("ids") List<Long> ids) {
        return couponUsageManager.batchStackInfo(ids);
    }
}
