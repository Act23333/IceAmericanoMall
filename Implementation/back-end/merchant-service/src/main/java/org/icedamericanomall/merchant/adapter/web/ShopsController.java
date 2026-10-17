package org.icedamericanomall.merchant.adapter.web;

import lombok.RequiredArgsConstructor;
import org.icedamericanomall.common.api.Result;
import org.icedamericanomall.merchant.adapter.response.ShopVO;
import org.icedamericanomall.merchant.app.seller.ShopManager;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 店铺公开页（新规范路径，ADR-019）—— 仅 {@code shop.status=1}（正常）可见。
 *
 * <pre>
 * Scenario: 访客查看正常营业的店铺
 *   Given 店铺 shopId 存在且状态为正常
 *   When GET /api/shops/{shopId}
 *   Then 返回 ShopVO（shopId/sellerId/shopName/shopLogo/contactPhone/address/status）
 *
 * Scenario: 查看不存在的店铺
 *   When GET /api/shops/{shopId}
 *   Then 抛 SHOP_NOT_FOUND（3015，纯「资源不存在」）
 *
 * Scenario: 店铺存在但状态非正常（审核中 / 冻结）
 *   Given 店铺 shopId 存在且 status != 1
 *   When GET /api/shops/{shopId}
 *   Then 抛 BUSINESS_EXECUTION_EXCEPTION（3000）+ 自定义文案，HTTP 200
 *     —— 这是「业务规则拒绝」，不是 not-found（批次 ③ 拆双义错误码）
 * </pre>
 */
@RestController
@RequestMapping("/api/shops")
@RequiredArgsConstructor
public class ShopsController {

    private final ShopManager shopManager;

    /** 按店铺 ID 查询公开店铺信息。 */
    @GetMapping("/{shopId}")
    public Result<ShopVO> getShop(@PathVariable Long shopId) {
        return Result.ok(shopManager.getVisibleShop(shopId));
    }
}
