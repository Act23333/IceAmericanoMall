package org.icedamericanomall.search.adapter.web;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.icedamericanomall.common.api.Result;
import org.icedamericanomall.common.security.UserContext;
import org.icedamericanomall.search.adapter.assembler.ProductSearchConverter;
import org.icedamericanomall.search.adapter.request.SearchPageReq;
import org.icedamericanomall.search.adapter.response.ProductSearchVO;
import org.icedamericanomall.search.app.SearchManager;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 商品搜索接口（前台）— 公开访问（无需登录）。
 *
 * <p>控制器仅做参数装配与契约转换（领域分页 → MP 分页信封），编排与检索在 {@link SearchManager}。
 */
@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchManager searchManager;
    private final ProductSearchConverter converter;

    /** 关键词搜索 — query 参数由 {@link SearchPageReq} 属性绑定（参数名不变） */
    @GetMapping("/product")
    public Result<Page<ProductSearchVO>> search(SearchPageReq req) {
        return Result.ok(converter.toPage(
                searchManager.search(req.getKeyword(), req.getCategoryId(), req.getPage(), req.getSize())));
    }

    /** 热门搜索词 */
    @GetMapping("/hot")
    public Result<List<String>> hotKeywords(
            @RequestParam(defaultValue = "10") int limit) {
        return Result.ok(searchManager.getHotKeywords(limit));
    }

    /** 我的搜索历史 */
    @GetMapping("/history")
    public Result<List<String>> history(
            @RequestParam(defaultValue = "10") int limit) {
        Long userId = UserContext.getUserId();
        return Result.ok(searchManager.getSearchHistory(userId, limit));
    }
}
