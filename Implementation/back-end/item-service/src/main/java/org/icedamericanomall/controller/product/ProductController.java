package org.icedamericanomall.controller.product;

import com.baomidou.mybatisplus.core.metadata.IPage;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.icedamericanomall.domain.dto.CreateProductReq;
import org.icedamericanomall.domain.dto.ProductPageReq;
import org.icedamericanomall.domain.dto.UpdateProductReq;
import org.icedamericanomall.domain.entity.SkuEntity;
import org.icedamericanomall.domain.vo.ProductVO;
import org.icedamericanomall.service.ProductService;
import org.noLazy.common.domain.Result;
import org.noLazy.common.utils.UserContext;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 商品 Controller — 京东标准：同一资源一个 Controller
 *
 * GET   /api/item/product/page      — 游客/用户/VIP/SVIP 浏览商品列表
 * GET   /api/item/product/{id}      — 商品详情
 * GET   /api/item/product/{id}/detail — 聚合详情(V3.5)
 * GET   /api/item/product/{id}/skus  — SKU列表(V3.5)
 * POST  /api/item/product            — 商家发布商品
 * PUT   /api/item/product/{id}       — 商家编辑商品
 * PUT   /api/item/product/{id}/status — 商家/管理员上下架
 */
@RestController
@RequestMapping("/api/item/product")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    // ==================== 查询 (游客/用户/VIP/SVIP 共享) ====================

    @GetMapping("/page")
    public Result<IPage<ProductVO>> page(ProductPageReq req) {
        IPage<ProductVO> page = productService.pageProducts(
                req.getCategoryId(), req.getKeyword(),
                req.getSort(), req.getOrder(),
                req.getPage(), req.getSize());
        return Result.ok(page);
    }

    @GetMapping("/{id}")
    public Result<ProductVO> detail(@PathVariable Long id) {
        return Result.ok(productService.getProductDetail(id));
    }

    /** V3.5: 聚合详情 — 商品+店铺+SKU+评论摘要 */
    @GetMapping("/{id}/detail")
    public Result<?> getProductDetail(@PathVariable Long id) {
        return Result.ok(productService.getProductDetail(id));
    }

    /** V3.5: SKU 列表 */
    @GetMapping("/{id}/skus")
    public Result<List<SkuEntity>> getSkuList(@PathVariable Long id) {
        return Result.ok(productService.listSkus(id));
    }

    // ==================== 商家操作 ====================

    @PostMapping
    public Result<Void> create(@Valid @RequestBody CreateProductReq req) {
        Long sellerId = UserContext.getUserId();
        productService.createProduct(sellerId, req);
        return Result.ok();
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody UpdateProductReq req) {
        Long sellerId = UserContext.getUserId();
        productService.updateProduct(id, sellerId, req);
        return Result.ok();
    }

    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        productService.updateProductStatus(id, status);
        return Result.ok();
    }
}
