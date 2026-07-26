package org.icedamericanomall.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.icedamericanomall.domain.dto.CreateProductReq;
import org.icedamericanomall.domain.dto.ProductPageReq;
import org.icedamericanomall.domain.dto.UpdateProductReq;
import org.icedamericanomall.domain.vo.ProductVO;
import org.icedamericanomall.service.ProductService;
import org.noLazy.common.domain.Result;
import org.noLazy.common.utils.UserContext;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/item/product")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

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

    @PostMapping
    public Result<Void> create(@Valid @RequestBody CreateProductReq req) {
        Long sellerId = UserContext.getUserId();
        productService.createProduct(sellerId, req);
        return Result.ok();
    }

    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        productService.updateProductStatus(id, status);
        return Result.ok();
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody UpdateProductReq req) {
        Long sellerId = UserContext.getUserId();
        productService.updateProduct(id, sellerId, req);
        return Result.ok();
    }
}
