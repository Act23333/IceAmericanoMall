package org.icedamericanomall.search.app;

import lombok.RequiredArgsConstructor;
import org.icedamericanomall.search.domain.gateway.KeywordTracker;
import org.icedamericanomall.search.domain.gateway.SpuSearchGateway;
import org.icedamericanomall.search.domain.gateway.SpuSuggestGateway;
import org.icedamericanomall.search.domain.model.search.SpuSearchHit;
import org.icedamericanomall.search.domain.model.search.SearchFacets;
import org.icedamericanomall.search.domain.model.search.SearchPage;
import org.icedamericanomall.search.domain.model.search.SearchQuery;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * 搜索编排（应用层）—— 关键词记录规则 + 委托检索网关 + SE5 联想双源合并。
 *
 * <p>职责：非空关键词才记录热搜（规则内聚于此，DB/ES 两条实现链不再各自为政）；
 * 组装 {@link SearchQuery} 委托 {@link SpuSearchGateway}（DB LIKE / ES 条件装配）；
 * SE5 联想合并热搜前缀与商品名前缀两源（排序/去重/截断口径内聚于此）。
 */
@Component
@RequiredArgsConstructor
public class SearchManager {

    /** 联想词上限（契约 SE5：≤10）。 */
    private static final int SUGGEST_LIMIT = 10;

    /**
     * 联想前缀长度上限（宽松防御：超长入参截断后仍可联想，不视为错误；
     * 50 字符已覆盖现实搜索前缀，防滥用超长串打满 LIKE/Redis）。
     */
    private static final int MAX_SUGGEST_PREFIX_LENGTH = 50;

    private final SpuSearchGateway spuSearchGateway;
    private final KeywordTracker keywordTracker;
    private final SpuSuggestGateway spuSuggestGateway;

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
    public SearchPage<SpuSearchHit> search(String keyword, Long categoryId, String brand,
                                               Integer minPrice, Integer maxPrice, String sort,
                                               int page, int size) {
        if (keyword != null && !keyword.isBlank()) {
            keywordTracker.recordKeyword(keyword.trim());
        }
        return spuSearchGateway.search(
                new SearchQuery(keyword, categoryId, page, size, brand, minPrice, maxPrice, sort));
    }

    /**
     * 搜索 facet 聚合（SE6）—— 品牌与固定价格档位计数。
     * 只透传 keyword/categoryId（facet 反映过滤前分布，不含 brand/price 过滤）。
     */
    public SearchFacets getFacets(String keyword, Long categoryId) {
        return spuSearchGateway.facets(new SearchQuery(keyword, categoryId, 1, 1));
    }

    /** 向量语义检索（DB 实现恒返回空页，ES 走 kNN）。 */
    public List<SpuSearchHit> vectorSearch(double[] embedding, int size) {
        return spuSearchGateway.vectorSearch(embedding, size);
    }

    /** 热门搜索词 Top-N。 */
    public List<String> getHotKeywords(int limit) {
        return keywordTracker.getHotKeywords(limit);
    }

    /**
     * 搜索联想（契约 SE5）：热搜词前缀命中在前 + 商品名前缀在后，精确去重，截 10。
     *
     * <p>双源口径：热搜源走 {@link KeywordTracker#getHotKeywordsByPrefix(String, int)}
     * （Redis，实现方保证异常降级空列表）；商品名源恒走 DB LIKE（{@link SpuSuggestGateway}，
     * ES 关闭可用）。两源各自 ≤10，合并后按源顺序去重（热搜命中优先展示，同名商品不重复出现）。
     *
     * <p>宽松入参口径：keyword 缺失/空白返回空列表（联想是输入辅助，不构成参数错误）；
     * 超长前缀截断到 {@value MAX_SUGGEST_PREFIX_LENGTH} 字符后照常联想。
     * 联想不记录热搜（浏览行为非检索行为，避免联想请求污染热搜榜）。
     *
     * @param keyword 前缀关键词（可空）
     * @return 联想词列表（≤10）；无命中为空列表
     */
    public List<String> suggest(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return List.of();
        }
        String prefix = truncate(keyword.trim());
        List<String> hotWords = keywordTracker.getHotKeywordsByPrefix(prefix, SUGGEST_LIMIT);
        List<String> productNames = spuSuggestGateway.suggestNames(prefix, SUGGEST_LIMIT);
        return Stream.concat(hotWords.stream(), productNames.stream())
                .distinct()
                .limit(SUGGEST_LIMIT)
                .toList();
    }

    private static String truncate(String prefix) {
        return prefix.length() > MAX_SUGGEST_PREFIX_LENGTH
                ? prefix.substring(0, MAX_SUGGEST_PREFIX_LENGTH)
                : prefix;
    }

    /** 我的搜索历史。 */
    public List<String> getSearchHistory(Long userId, int limit) {
        return keywordTracker.getSearchHistory(userId, limit);
    }

    /** ES 全量重建索引（DB 实现返回未启用标记）。 */
    public Map<String, Object> reindex() {
        return spuSearchGateway.reindex();
    }
}
