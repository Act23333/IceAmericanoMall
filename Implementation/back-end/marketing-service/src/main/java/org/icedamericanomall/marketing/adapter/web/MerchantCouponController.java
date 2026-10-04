package org.icedamericanomall.marketing.adapter.web;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.icedamericanomall.common.api.Result;
import org.icedamericanomall.common.security.UserContext;
import org.icedamericanomall.marketing.adapter.request.CouponCreateReq;
import org.icedamericanomall.marketing.adapter.response.CouponVO;
import org.icedamericanomall.marketing.app.coupon.CouponAdminManager;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 商家店铺优惠券 — 商家为自己的店铺创建和管理优惠券。
 *
 * <p>店铺归属取登录态（禁止由请求体指定 sellerId，防越权）；查询与状态流转由
 * {@link CouponAdminManager} 完成。
 *
 * <p><b>归位说明</b>：适配层子包按受众分组（web/admin/internal）——商家端 {@code /api/seller/**}
 * 与前台同属 web（分组维度是受众，不是业务功能）。
 */
@RestController
@RequestMapping("/api/seller/coupon")
@RequiredArgsConstructor
public class MerchantCouponController {

    private final CouponAdminManager couponAdminManager;

    /** 创建店铺优惠券（归属当前登录商家）。 */
    @PostMapping
    public Result<CouponVO> create(@RequestBody @Valid CouponCreateReq req) {
        return Result.ok(couponAdminManager.createShopCoupon(UserContext.getUserId(), req));
    }

    /** 我的店铺优惠券列表（按下发时间倒序）。 */
    @GetMapping
    public Result<List<CouponVO>> list() {
        return Result.ok(couponAdminManager.listShopCoupons(UserContext.getUserId()));
    }

    /** 下线本店优惠券；非本店券按「优惠券不存在」返回（不泄露他人券存在性）。 */
    @PutMapping("/{couponId}/disable")
    public Result<CouponVO> disable(@PathVariable String couponId) {
        return Result.ok(couponAdminManager.disableShopCoupon(couponId, UserContext.getUserId()));
    }
}
