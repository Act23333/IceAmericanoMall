package org.icedAmericanoMall.controller;

import lombok.RequiredArgsConstructor;
import org.icedAmericanoMall.domain.entity.CouponEntity;
import org.icedAmericanoMall.service.CouponService;
import org.noLazy.common.domain.Result;
import org.springframework.security.access.prepost.PreAuthorize;
import org.noLazy.common.enums.ErrorCode;
import org.noLazy.common.exception.BizException;
import org.springframework.web.bind.annotation.*;

/**
 * 管理后台优惠券接口。
 */
@RestController
@PreAuthorize("@ss.hasPermi('user:admin')")
@RequestMapping("/api/admin/coupon")
@RequiredArgsConstructor
public class AdminCouponController {

    private final CouponService couponService;

    /** 创建优惠券模板 */
    @PostMapping
    public Result<CouponEntity> create(@RequestBody CouponEntity entity) {
        entity.setIssuedQty(0);
        couponService.save(entity);
        return Result.ok(entity);
    }

    /** 下线优惠券 */
    @PutMapping("/{couponId}/disable")
    public Result<?> disable(@PathVariable String couponId) {
        CouponEntity c = couponService.lambdaQuery()
                .eq(CouponEntity::getCouponId, couponId).one();
        if (c == null) throw new BizException(ErrorCode.USER_NOT_FOUND, "优惠券不存在");
        c.setStatus(0);
        couponService.updateById(c);
        return Result.ok();
    }
}
