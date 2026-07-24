package org.icedAmericanoMall.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedAmericanoMall.domain.entity.ProductEntity;
import org.icedAmericanoMall.domain.entity.SkuEntity;
import org.icedAmericanoMall.mapper.ProductMapper;
import org.icedAmericanoMall.mapper.SkuMapper;
import org.noLazy.common.domain.Result;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * V3.5: 商品详情页数据聚合（京东标准 — 商品+店铺+SKU+评论摘要）。
 */
@Slf4j
@RestController
@RequestMapping("/api/item/product")
@RequiredArgsConstructor
public class ProductDetailController {

    private final ProductMapper productMapper;
    private final SkuMapper skuMapper;

    @GetMapping("/{id}/detail")
    public Result<Map<String, Object>> getProductDetail(@PathVariable Long id) {
        ProductEntity product = productMapper.selectById(id);
        if (product == null) return Result.error(404, "商品不存在");

        List<SkuEntity> skus = skuMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SkuEntity>()
                        .eq(SkuEntity::getProductId, id)
                        .eq(SkuEntity::getStatus, 1));

        int minPrice = skus.stream().mapToInt(SkuEntity::getPrice).min().orElse(0);
        int minOriginal = skus.stream()
                .filter(s -> s.getOriginalPrice() != null)
                .mapToInt(SkuEntity::getOriginalPrice).min().orElse(minPrice);

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("productId", product.getProductId());
        detail.put("name", product.getName());
        detail.put("description", product.getDescription());
        detail.put("brand", product.getBrand());
        detail.put("images", product.getImages());
        detail.put("videoUrl", product.getVideoUrl());
        detail.put("attributes", product.getAttributes());
        detail.put("serviceTags", product.getServiceTags());
        detail.put("price", minPrice);              // 到手价
        detail.put("originalPrice", minOriginal);   // 原价
        detail.put("soldCount", product.getSoldCount());
        detail.put("viewCount", product.getViewCount());
        detail.put("commentCount", product.getCommentCount());
        detail.put("skus", skus);
        // TODO: 聚合 Seller info + ReviewSummary + 可用优惠券
        return Result.ok(detail);
    }

    @GetMapping("/{id}/skus")
    public Result<List<SkuEntity>> getSkuList(@PathVariable Long id) {
        return Result.ok(skuMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SkuEntity>()
                        .eq(SkuEntity::getProductId, id)
                        .eq(SkuEntity::getStatus, 1)));
    }
}
