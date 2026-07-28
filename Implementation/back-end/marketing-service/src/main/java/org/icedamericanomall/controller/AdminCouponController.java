package org.icedamericanomall.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.icedamericanomall.constants.GrabTypeEnum;
import org.icedamericanomall.convert.MarketingConverter;
import org.icedamericanomall.domain.dto.CouponCreateReq;
import org.icedamericanomall.domain.entity.CouponEntity;
import org.icedamericanomall.service.CouponService;
import org.icedamericanomall.service.impl.CouponGrabLuaScript;
import org.noLazy.common.domain.Result;
import org.noLazy.common.enums.ErrorCode;
import org.noLazy.common.exception.BizException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 管理后台优惠券接口。
 * V4.0: 使用 DTO 替代 Entity 作为请求体（Alibaba 规范）；NEED_GRAB 券自动预热 Redis 库存。
 */
@RestController
@PreAuthorize("@ss.hasPermi('user:admin')")
@RequestMapping("/api/admin/coupon")
@RequiredArgsConstructor
public class AdminCouponController {

    private final CouponService couponService;
    private final MarketingConverter marketingConverter;
    private final CouponGrabLuaScript couponGrabLuaScript;

    /** V4.0: 创建优惠券模板（使用 @Valid DTO，Alibaba 规范） */
    @PostMapping
    public Result<CouponEntity> create(@RequestBody @Valid CouponCreateReq req) {
        CouponEntity entity = marketingConverter.toEntity(req);
        entity.setIssuedQty(0);
        couponService.save(entity);

        // V4.0: NEED_GRAB 类型优惠券自动预热 Redis 库存
        if (req.getGrabType() != null && req.getGrabType() == GrabTypeEnum.NEED_GRAB.getCode()
                && req.getTotalQty() != null && req.getTotalQty() > 0) {
            couponGrabLuaScript.preloadStock(entity.getId(), req.getTotalQty());
        }

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
