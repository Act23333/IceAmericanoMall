package org.icedamericanomall.controller;

import lombok.RequiredArgsConstructor;
import org.icedamericanomall.domain.entity.SkuEntity;
import org.icedamericanomall.service.ProductService;
import org.noLazy.common.domain.Result;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * V3.5: 商品详情页数据聚合（京东标准 — 商品+SKU）。
 * <p>
 * DDD: Controller 只做参数解析+委托Service，业务逻辑全在 Service 层。
 */
@RestController
@RequestMapping("/api/item/product")
@RequiredArgsConstructor
public class ProductDetailController {

    private final ProductService productService;

    @GetMapping("/{id}/detail")
    public Result<?> getProductDetail(@PathVariable Long id) {
        return Result.ok(productService.getProductDetail(id));
    }

    @GetMapping("/{id}/skus")
    public Result<List<SkuEntity>> getSkuList(@PathVariable Long id) {
        return Result.ok(productService.listSkus(id));
    }
}
