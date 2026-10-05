package org.icedamericanomall.item.adapter.web;

import com.baomidou.mybatisplus.core.metadata.IPage;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.icedamericanomall.common.api.Result;
import org.icedamericanomall.common.security.UserContext;
import org.icedamericanomall.item.adapter.request.CreateProductReq;
import org.icedamericanomall.item.adapter.request.ProductPageReq;
import org.icedamericanomall.item.adapter.request.UpdateProductReq;
import org.icedamericanomall.item.adapter.response.ProductVO;
import org.icedamericanomall.item.adapter.response.RecommendProductVO;
import org.icedamericanomall.item.adapter.response.SkuVO;
import org.icedamericanomall.item.app.ProductManager;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 商品 Controller — 京东标准：同一资源一个 Controller
 *
 * GET   /api/item/product/page      — 游客/用户/VIP/SVIP 浏览商品列表
 * GET   /api/item/product/{id}      — 商品详情（含完整 SKU）
 * GET   /api/item/product/{id}/skus  — SKU列表
 * POST  /api/item/product            — 商家发布商品
 * PUT   /api/item/product/{id}       — 商家编辑商品
 * PUT   /api/item/product/{id}/status — 商家/管理员上下架
 */
@RestController
@RequestMapping("/api/item/product")
@RequiredArgsConstructor
public class ProductController {

    private final ProductManager productManager;

    // ==================== 查询 (游客/用户/VIP/SVIP 共享) ====================

    @GetMapping("/page")
    public Result<IPage<ProductVO>> page(ProductPageReq req) {
        IPage<ProductVO> page = productManager.pageProducts(
                req.getSellerId(), req.getCategoryId(), req.getKeyword(),
                req.getSort(), req.getOrder(),
                req.getPage(), req.getSize());
        return Result.ok(page);
    }

    @GetMapping("/{id}")
    public Result<ProductVO> detail(@PathVariable Long id) {
        return Result.ok(productManager.getProductDetail(id));
    }

    /** SKU 列表（对外返回 VO，不泄漏 DO） */
    @GetMapping("/{id}/skus")
    public Result<List<SkuVO>> getSkuList(@PathVariable Long id) {
        return Result.ok(productManager.listSkus(id));
    }

    /**
     * 相似商品推荐（P27，ADR-046）—— 向量相似度降序，恒过滤在售并排除自身；
     * ES 关闭/无相似命中 → 空列表不报错（推荐位可空）。
     */
    @GetMapping("/{id}/similar")
    public Result<List<RecommendProductVO>> similar(@PathVariable Long id,
                                                    @RequestParam(required = false) Integer size) {
        return Result.ok(productManager.similarProducts(id, size));
    }

    // ==================== 商家操作 ====================

    @PostMapping
    public Result<Void> create(@Valid @RequestBody CreateProductReq req) {
        Long sellerId = UserContext.getUserId();
        productManager.createProduct(sellerId, req);
        return Result.ok();
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody UpdateProductReq req) {
        Long sellerId = UserContext.getUserId();
        productManager.updateProduct(id, sellerId, req);
        return Result.ok();
    }

    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        productManager.updateProductStatus(id, status);
        return Result.ok();
    }
}
