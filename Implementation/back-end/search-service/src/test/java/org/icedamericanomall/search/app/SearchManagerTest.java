package org.icedamericanomall.search.app;

import org.icedamericanomall.search.domain.enums.SearchSortEnum;
import org.icedamericanomall.search.domain.gateway.KeywordTracker;
import org.icedamericanomall.search.domain.gateway.ProductSearchGateway;
import org.icedamericanomall.search.domain.model.search.SearchQuery;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * SearchManager 单元测试 —— SE1 参数透传与热搜记录规则、SE6 facets 委托（只带 keyword/categoryId）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("SearchManager — SE1 参数透传 + SE6 facets 委托")
class SearchManagerTest {

    @Mock
    private ProductSearchGateway productSearchGateway;

    @Mock
    private KeywordTracker keywordTracker;

    private SearchManager manager() {
        return new SearchManager(productSearchGateway, keywordTracker);
    }

    @Test
    @DisplayName("search — SE1 全部过滤参数透传网关（brand/minPrice/maxPrice/sort/分页）")
    void shouldPassAllSe1ParamsToGateway_whenSearch() {
        manager().search("手机", 10L, "SoundMax", 100, 200, "price", 2, 10);

        ArgumentCaptor<SearchQuery> captor = ArgumentCaptor.forClass(SearchQuery.class);
        verify(productSearchGateway).search(captor.capture());
        SearchQuery query = captor.getValue();
        assertEquals("手机", query.keyword());
        assertEquals(10L, query.categoryId());
        assertEquals("SoundMax", query.brand());
        assertEquals(100, query.minPrice());
        assertEquals(200, query.maxPrice());
        assertEquals("price", query.sort());
        assertEquals(2, query.page());
        assertEquals(10, query.size());
    }

    @Test
    @DisplayName("search — 非空关键词记录热搜（去除首尾空白）")
    void shouldRecordTrimmedHotKeyword_whenKeywordPresent() {
        manager().search(" 手机 ", null, null, null, null, null, 1, 10);

        verify(keywordTracker).recordKeyword("手机");
    }

    @Test
    @DisplayName("search — 空白关键词不记录热搜")
    void shouldNotRecordHotKeyword_whenKeywordBlank() {
        manager().search("   ", null, null, null, null, null, 1, 10);

        verify(keywordTracker, never()).recordKeyword(any());
    }

    @Test
    @DisplayName("getFacets — 只透传 keyword/categoryId（brand/price 不参与 facet，反映过滤前分布）")
    void shouldDelegateFacetsWithKeywordAndCategoryOnly_whenGetFacets() {
        manager().getFacets("手机", 10L);

        ArgumentCaptor<SearchQuery> captor = ArgumentCaptor.forClass(SearchQuery.class);
        verify(productSearchGateway).facets(captor.capture());
        SearchQuery query = captor.getValue();
        assertEquals("手机", query.keyword());
        assertEquals(10L, query.categoryId());
        assertNull(query.brand());
        assertNull(query.minPrice());
        assertNull(query.maxPrice());
        assertNull(query.sort());
    }

    @Test
    @DisplayName("search — 未知 sort 宽松回落 relevance（默认相关度即现行为）")
    void shouldFallbackRelevance_whenSortUnknown() {
        manager().search("手机", null, null, null, null, "bogus", 1, 10);

        ArgumentCaptor<SearchQuery> captor = ArgumentCaptor.forClass(SearchQuery.class);
        verify(productSearchGateway).search(captor.capture());
        assertEquals(SearchSortEnum.RELEVANCE, captor.getValue().sortOrDefault());
    }
}
