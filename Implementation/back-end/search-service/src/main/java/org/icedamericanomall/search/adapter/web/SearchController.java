package org.icedamericanomall.search.adapter.web;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.icedamericanomall.common.api.Result;
import org.icedamericanomall.common.security.UserContext;
import org.icedamericanomall.search.adapter.assembler.ProductSearchConverter;
import org.icedamericanomall.search.adapter.request.SearchPageReq;
import org.icedamericanomall.search.adapter.response.ProductSearchVO;
import org.icedamericanomall.search.adapter.response.SearchFacetsVO;
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

    /**
     * 关键词搜索（SE1）— query 参数由 {@link SearchPageReq} 属性绑定（参数名不变；
     * V5.10 补齐 {@code brand}/{@code minPrice}/{@code maxPrice}/{@code sort} 过滤透传）。
     */
    @GetMapping("/product")
    public Result<Page<ProductSearchVO>> search(SearchPageReq req) {
        return Result.ok(converter.toPage(searchManager.search(req.getKeyword(), req.getCategoryId(),
                req.getBrand(), req.getMinPrice(), req.getMaxPrice(), req.getSort(),
                req.getPage(), req.getSize())));
    }

    /**
     * 搜索 facet（SE6）— 品牌与固定价格档位计数（反映 keyword/categoryId 过滤前分布）。
     * 复用 {@link SearchPageReq} 绑定，仅取 keyword/categoryId（page/size 被忽略）；
     * ES 链实现，DB 链与聚合异常降级为空对象。
     */
    @GetMapping("/facets")
    public Result<SearchFacetsVO> facets(SearchPageReq req) {
        return Result.ok(converter.toVO(searchManager.getFacets(req.getKeyword(), req.getCategoryId())));
    }

    /** 热门搜索词 */
    @GetMapping("/hot")
    public Result<List<String>> hotKeywords(
            @RequestParam(defaultValue = "10") int limit) {
        return Result.ok(searchManager.getHotKeywords(limit));
    }

    /**
     * 搜索联想（SE5）— 热搜词前缀命中在前 + 商品名前缀在后，去重 ≤10（ES 关闭可用）。
     * keyword 缺失/空白按宽松口径返回空列表（联想是输入辅助，不视为参数错误，注释见契约）。
     */
    @GetMapping("/suggest")
    public Result<List<String>> suggest(
            @RequestParam(value = "keyword", required = false) String keyword) {
        return Result.ok(searchManager.suggest(keyword));
    }

    /** 我的搜索历史 */
    @GetMapping("/history")
    public Result<List<String>> history(
            @RequestParam(defaultValue = "10") int limit) {
        Long userId = UserContext.getUserId();
        return Result.ok(searchManager.getSearchHistory(userId, limit));
    }
}
