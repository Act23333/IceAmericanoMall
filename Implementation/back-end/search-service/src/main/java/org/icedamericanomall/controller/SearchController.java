package org.icedamericanomall.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.icedamericanomall.domain.vo.ProductSearchVO;
import org.icedamericanomall.service.SearchService;
import org.noLazy.common.domain.Result;
import org.noLazy.common.utils.UserContext;
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

    /** 关键词搜索 */
    @GetMapping("/product")
    public Result<Page<ProductSearchVO>> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(searchService.search(keyword, categoryId, page, size));
    }

    /** 热门搜索词 */
    @GetMapping("/hot")
    public Result<List<String>> hotKeywords(
            @RequestParam(defaultValue = "10") int limit) {
        return Result.ok(searchService.getHotKeywords(limit));
    }

    /** 我的搜索历史 */
    @GetMapping("/history")
    public Result<List<String>> history(
            @RequestParam(defaultValue = "10") int limit) {
        Long userId = UserContext.getUserId();
        return Result.ok(searchService.getSearchHistory(userId, limit));
    }
}
