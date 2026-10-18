package org.icedamericanomall.marketing.adapter.admin;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.icedamericanomall.common.annotation.OperationLog;
import org.icedamericanomall.common.api.Result;
import org.icedamericanomall.marketing.adapter.request.CouponCreateReq;
import org.icedamericanomall.marketing.adapter.response.CouponVO;
import org.icedamericanomall.marketing.app.coupon.CouponAdminManager;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理后台优惠券接口。
 *
 * <p>V4.0：请求体用 {@code @Valid} DTO、响应体用 VO（Alibaba 规范）；
 * 创建与下线逻辑（含 NEED_GRAB 券的 Redis 预热）下沉到 {@link CouponAdminManager}。
 *
 * <p><b>审计（ADR-032 管道一）</b>：创建与下线两个写操作标注 {@code @OperationLog}，
 * 产出单行 JSON 审计日志（logger {@code AUDIT}，经 ELK 采集，不落库）；含请求体的创建端点
 * 默认不记参数值（PII 安全默认），仅券号标识的下线端点开启参数摘要。
 */
@RestController
// 权限位拆分（5a 批 7，2026-10-18）：优惠券管理（发/停），券是折扣承诺不是资金划转
@PreAuthorize("@ss.hasPermi('marketing:admin')")
@RequestMapping("/api/admin/coupon")
@RequiredArgsConstructor
public class AdminCouponController {

    /** 审计模块：营销域优惠券。 */
    private static final String MODULE_MARKETING_COUPON = "MARKETING_COUPON";

    /** 审计动作：创建优惠券模板。 */
    private static final String ACTION_CREATE_COUPON = "CREATE_COUPON";

    /** 审计动作：下线优惠券。 */
    private static final String ACTION_DISABLE_COUPON = "DISABLE_COUPON";

    private final CouponAdminManager couponAdminManager;

    /** V4.0: 创建优惠券模板（使用 @Valid DTO，Alibaba 规范）。 */
    @OperationLog(module = MODULE_MARKETING_COUPON, action = ACTION_CREATE_COUPON, description = "创建优惠券")
    @PostMapping
    public Result<CouponVO> create(@RequestBody @Valid CouponCreateReq req) {
        return Result.ok(couponAdminManager.createCoupon(req));
    }

    /** 下线优惠券（幂等：已下线重复调用同样返回当前状态）。 */
    @OperationLog(module = MODULE_MARKETING_COUPON, action = ACTION_DISABLE_COUPON, description = "下线优惠券",
            recordParams = true)
    @PutMapping("/{couponId}/disable")
    public Result<CouponVO> disable(@PathVariable String couponId) {
        return Result.ok(couponAdminManager.disableCoupon(couponId));
    }
}
