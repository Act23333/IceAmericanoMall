package org.icedAmericanoMall.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.icedAmericanoMall.domain.dto.ProductSearchResult;
import org.icedAmericanoMall.service.SearchService;
import org.noLazy.common.domain.Result;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 商品搜索接口 — 公开访问（无需登录）。
 */
@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;

    @GetMapping("/product")
    public Result<Page<ProductSearchResult>> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(searchService.search(keyword, categoryId, page, size));
    }

    @GetMapping("/hot")
    public Result<List<String>> hotKeywords() {
        return Result.ok(searchService.getHotKeywords());
    }
}
