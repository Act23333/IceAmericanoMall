package org.icedamericanomall.search.app;

import lombok.RequiredArgsConstructor;
import org.icedamericanomall.search.domain.gateway.KeywordTracker;
import org.icedamericanomall.search.domain.gateway.ProductSearchGateway;
import org.icedamericanomall.search.domain.model.search.ProductSearchHit;
import org.icedamericanomall.search.domain.model.search.SearchFacets;
import org.icedamericanomall.search.domain.model.search.SearchPage;
import org.icedamericanomall.search.domain.model.search.SearchQuery;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 搜索编排（应用层）—— 关键词记录规则 + 委托检索网关。
 *
 * <p>职责：非空关键词才记录热搜（规则内聚于此，DB/ES 两条实现链不再各自为政）；
 * 组装 {@link SearchQuery} 委托 {@link ProductSearchGateway}（DB LIKE / ES 条件装配）。
 */
@Component
@RequiredArgsConstructor
public class SearchManager {

    private final ProductSearchGateway productSearchGateway;
    private final KeywordTracker keywordTracker;

    /**
     * 关键词/分类/品牌/价格/排序检索（SE1，非空关键词记录热搜）。
     *
     * @param keyword   关键词（可空 = 全量浏览）
     * @param categoryId 分类过滤（可空）
     * @param brand     品牌精确过滤（可空）
     * @param minPrice  最低价过滤（可空，分）
     * @param maxPrice  最高价过滤（可空，分）
     * @param sort      排序 sales|price|newest|relevance（可空 = relevance）
     * @param page      页码（1 起）
     * @param size      每页条数
     */
    public SearchPage<ProductSearchHit> search(String keyword, Long categoryId, String brand,
                                               Integer minPrice, Integer maxPrice, String sort,
                                               int page, int size) {
        if (keyword != null && !keyword.isBlank()) {
            keywordTracker.recordKeyword(keyword.trim());
        }
        return productSearchGateway.search(
                new SearchQuery(keyword, categoryId, page, size, brand, minPrice, maxPrice, sort));
    }

    /**
     * 搜索 facet 聚合（SE6）—— 品牌与固定价格档位计数。
     * 只透传 keyword/categoryId（facet 反映过滤前分布，不含 brand/price 过滤）。
     */
    public SearchFacets getFacets(String keyword, Long categoryId) {
        return productSearchGateway.facets(new SearchQuery(keyword, categoryId, 1, 1));
    }

    /** 向量语义检索（DB 实现恒返回空页，ES 走 kNN）。 */
    public List<ProductSearchHit> vectorSearch(double[] embedding, int size) {
        return productSearchGateway.vectorSearch(embedding, size);
    }

    /** 热门搜索词 Top-N。 */
    public List<String> getHotKeywords(int limit) {
        return keywordTracker.getHotKeywords(limit);
    }

    /** 我的搜索历史。 */
    public List<String> getSearchHistory(Long userId, int limit) {
        return keywordTracker.getSearchHistory(userId, limit);
    }

    /** ES 全量重建索引（DB 实现返回未启用标记）。 */
    public Map<String, Object> reindex() {
        return productSearchGateway.reindex();
    }
}
