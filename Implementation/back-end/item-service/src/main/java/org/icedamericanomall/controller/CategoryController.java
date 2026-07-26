package org.icedamericanomall.controller;

import lombok.RequiredArgsConstructor;
import org.icedamericanomall.domain.entity.CategoryEntity;
import org.icedamericanomall.domain.vo.CategoryTreeVO;
import org.icedamericanomall.service.CategoryService;
import org.noLazy.common.domain.Result;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/item/category")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping("/tree")
    public Result<List<CategoryTreeVO>> getTree() {
        return Result.ok(categoryService.getCategoryTree());
    }

    @PostMapping
    public Result<Void> create(@RequestBody CategoryEntity entity) {
        categoryService.createCategory(entity);
        return Result.ok();
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody CategoryEntity entity) {
        entity.setId(id);
        categoryService.updateCategory(entity);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        categoryService.deleteCategory(id);
        return Result.ok();
    }
}
