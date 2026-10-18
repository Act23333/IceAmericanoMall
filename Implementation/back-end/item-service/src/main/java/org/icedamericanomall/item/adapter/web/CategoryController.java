package org.icedamericanomall.item.adapter.web;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.icedamericanomall.common.api.Result;
import org.icedamericanomall.item.adapter.request.CategoryReq;
import org.icedamericanomall.item.adapter.response.CategoryTreeVO;
import org.icedamericanomall.item.adapter.response.CategoryVO;
import org.icedamericanomall.item.app.CategoryManager;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 类目接口（{@code /api/item/category}）。
 *
 * <p><b>鉴权口径</b>：{@code GET /tree} 为公开读（网关「仅 GET 匿名」）；
 * 三个写操作（P9-P11，类目属平台级配置，{@code DELETE} 为物理删除）**仅管理员**，
 * 故 {@code @PreAuthorize} 标注在方法上而非类上——类级标注会连带封掉公开的 tree 查询。
 *
 * <p><b>权限位（Q8 裁决，2026-10-18）</b>：写操作挂 {@code product:admin}（平台级商品配置：类目/品牌/属性），
 * 不再复用管理端总开关 {@code user:admin}；也不新造 {@code category:admin} —— 类目只是平台商品配置的一种。
 * 与 {@code product:write} 的区别：后者是商家维护<b>自有</b>商品，前者是平台维护<b>全局</b>配置。
 */
@RestController
@RequestMapping("/api/item/category")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryManager categoryManager;

    @GetMapping("/tree")
    public Result<List<CategoryTreeVO>> getTree() {
        return Result.ok(categoryManager.getCategoryTree());
    }

    @PostMapping
    @PreAuthorize("@ss.hasPermi('product:admin')")
    public Result<CategoryVO> create(@Valid @RequestBody CategoryReq req) {
        return Result.ok(categoryManager.createCategory(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@ss.hasPermi('product:admin')")
    public Result<CategoryVO> update(@PathVariable Long id, @Valid @RequestBody CategoryReq req) {
        return Result.ok(categoryManager.updateCategory(id, req));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@ss.hasPermi('product:admin')")
    public Result<Void> delete(@PathVariable Long id) {
        categoryManager.deleteCategory(id);
        return Result.ok();
    }
}
