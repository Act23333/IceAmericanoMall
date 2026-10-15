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
    @PreAuthorize("@ss.hasPermi('user:admin')")
    public Result<CategoryVO> create(@Valid @RequestBody CategoryReq req) {
        return Result.ok(categoryManager.createCategory(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@ss.hasPermi('user:admin')")
    public Result<CategoryVO> update(@PathVariable Long id, @Valid @RequestBody CategoryReq req) {
        return Result.ok(categoryManager.updateCategory(id, req));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@ss.hasPermi('user:admin')")
    public Result<Void> delete(@PathVariable Long id) {
        categoryManager.deleteCategory(id);
        return Result.ok();
    }
}
