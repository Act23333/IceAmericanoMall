package org.icedamericanomall.controller;

import lombok.RequiredArgsConstructor;
import org.icedamericanomall.manager.ShopManager;
import org.noLazy.common.domain.Result;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * V3.5: 商家公开店铺页（DDD: Controller→Manager→Mapper）。
 */
@RestController
@RequestMapping("/api/shop")
@RequiredArgsConstructor
public class ShopController {

    private final ShopManager shopManager;

    @GetMapping("/{sellerId}")
    public Result<Map<String, Object>> getShopInfo(@PathVariable Long sellerId) {
        Map<String, Object> info = shopManager.getShopInfo(sellerId);
        return info != null ? Result.ok(info) : Result.error(404, "店铺不存在");
    }
}
