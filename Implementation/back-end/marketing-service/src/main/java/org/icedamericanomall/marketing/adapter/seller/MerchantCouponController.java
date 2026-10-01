package org.icedamericanomall.marketing.adapter.seller;

import lombok.RequiredArgsConstructor;
import org.icedamericanomall.marketing.domain.entity.CouponEntity;
import org.icedamericanomall.marketing.service.CouponService;
import org.icedamericanomall.common.domain.Result;
import org.icedamericanomall.common.domain.UserContext;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 商家店铺优惠券 — 商家为自己的店铺创建和管理优惠券。
 */
@RestController
@RequestMapping("/api/seller/coupon")
@RequiredArgsConstructor
public class MerchantCouponController {

    private final CouponService couponService;

    /** 创建店铺优惠券 */
    @PostMapping
    public Result<CouponEntity> create(@RequestBody CouponEntity entity) {
        entity.setSellerId(UserContext.getUserId());
        entity.setIssuedQty(0);
        couponService.save(entity);
        return Result.ok(entity);
    }

    /** 我的店铺优惠券列表 */
    @GetMapping
    public Result<List<CouponEntity>> list() {
        Long sellerId = UserContext.getUserId();
        return Result.ok(couponService.lambdaQuery()
                .eq(CouponEntity::getSellerId, sellerId)
                .orderByDesc(org.icedamericanomall.marketing.domain.entity.CouponEntity::getCreateTime)
                .list());
    }

    /** 下线优惠券 */
    @PutMapping("/{couponId}/disable")
    public Result<?> disable(@PathVariable String couponId) {
        Long sellerId = UserContext.getUserId();
        CouponEntity c = couponService.lambdaQuery()
                .eq(CouponEntity::getCouponId, couponId)
                .eq(CouponEntity::getSellerId, sellerId).one();
        if (c == null) return Result.ok();
        c.setStatus(0);
        couponService.updateById(c);
        return Result.ok();
    }
}
