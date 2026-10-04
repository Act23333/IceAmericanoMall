package org.icedamericanomall.search.app;

import lombok.RequiredArgsConstructor;
import org.icedamericanomall.search.domain.gateway.KeywordTracker;
import org.icedamericanomall.search.domain.gateway.ProductSearchGateway;
import org.icedamericanomall.search.domain.model.search.ProductSearchHit;
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

    /** 关键词/分类检索（非空关键词记录热搜）。 */
    public SearchPage<ProductSearchHit> search(String keyword, Long categoryId, int page, int size) {
        if (keyword != null && !keyword.isBlank()) {
            keywordTracker.recordKeyword(keyword.trim());
        }
        return productSearchGateway.search(new SearchQuery(keyword, categoryId, page, size));
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
