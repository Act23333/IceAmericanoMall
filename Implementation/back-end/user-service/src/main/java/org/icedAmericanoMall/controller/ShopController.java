package org.icedAmericanoMall.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.icedAmericanoMall.domain.entity.SellerEntity;
import org.icedAmericanoMall.mapper.SellerMapper;
import org.noLazy.common.domain.Result;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * V3.5: 商家公开店铺页（京东标准 — 店内商品浏览）。
 */
@RestController
@RequestMapping("/api/shop")
@RequiredArgsConstructor
public class ShopController {

    private final SellerMapper sellerMapper;

    @GetMapping("/{sellerId}")
    public Result<Map<String, Object>> getShopInfo(@PathVariable Long sellerId) {
        SellerEntity shop = sellerMapper.selectOne(
                new LambdaQueryWrapper<SellerEntity>()
                        .eq(SellerEntity::getUserId, sellerId)
                        .eq(SellerEntity::getStatus, 1));
        if (shop == null) return Result.error(404, "店铺不存在");

        Map<String, Object> info = new LinkedHashMap<>();
        info.put("sellerId", shop.getUserId());
        info.put("shopName", shop.getShopName());
        info.put("shopLogo", shop.getShopLogo());
        info.put("contactPhone", shop.getContactPhone());
        info.put("address", shop.getProvince() + shop.getCity() + shop.getDistrict());
        // followerCount 待 SellerEntity 增加字段
        return Result.ok(info);
    }
}
