package org.icedamericanomall.merchant.adapter.web;

import lombok.RequiredArgsConstructor;
import org.icedamericanomall.merchant.adapter.response.ShopVO;
import org.icedamericanomall.merchant.app.seller.ShopManager;
import org.icedamericanomall.common.api.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 商家公开店铺页（遗留兼容路径）。
 *
 * <p>V5.4（ADR-019）：本路径语义为 {@code sellerId}（商家 user_id）→ 该商家默认店铺
 * （{@code shop.id} 最小者）→ {@link ShopVO}，与旧契约字段
 * （{@code sellerId/shopName/shopLogo/contactPhone/address}）保持兼容；
 * 新接入方改用 {@code GET /api/shops/{shopId}}（{@link ShopsController}）。
 */
@RestController
@RequestMapping("/api/shop")
@RequiredArgsConstructor
public class ShopController {

    private final ShopManager shopManager;

    /**
     * 按商家用户 ID 查询默认店铺。
     *
     * @deprecated 改用 {@code GET /api/shops/{shopId}}（shopId 口径，支持多店铺）。
     */
    @Deprecated
    @GetMapping("/{sellerId}")
    public Result<ShopVO> getShopBySellerId(@PathVariable Long sellerId) {
        return Result.ok(shopManager.getLegacyShopBySellerId(sellerId));
    }
}
