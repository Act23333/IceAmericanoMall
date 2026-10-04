package org.icedamericanomall.item.adapter.web;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.icedamericanomall.common.api.Result;
import org.icedamericanomall.item.adapter.request.CategoryReq;
import org.icedamericanomall.item.adapter.response.CategoryTreeVO;
import org.icedamericanomall.item.adapter.response.CategoryVO;
import org.icedamericanomall.item.app.CategoryManager;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
    public Result<CategoryVO> create(@Valid @RequestBody CategoryReq req) {
        return Result.ok(categoryManager.createCategory(req));
    }

    @PutMapping("/{id}")
    public Result<CategoryVO> update(@PathVariable Long id, @Valid @RequestBody CategoryReq req) {
        return Result.ok(categoryManager.updateCategory(id, req));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        categoryManager.deleteCategory(id);
        return Result.ok();
    }
}
