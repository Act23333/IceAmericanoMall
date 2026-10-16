package org.icedamericanomall.search.app;

import org.icedamericanomall.search.domain.enums.SearchSortEnum;
import org.icedamericanomall.search.domain.gateway.KeywordTracker;
import org.icedamericanomall.search.domain.gateway.SpuSearchGateway;
import org.icedamericanomall.search.domain.gateway.SpuSuggestGateway;
import org.icedamericanomall.search.domain.model.search.SearchQuery;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * SearchManager 单元测试 —— SE1 参数透传与热搜记录规则、SE6 facets 委托（只带 keyword/categoryId）、
 * SE5 联想双源合并（热搜在前 + 商品名在后、去重、截 10、宽松入参）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("SearchManager — SE1 参数透传 + SE6 facets 委托 + SE5 联想合并")
class SearchManagerTest {

    @Mock
    private SpuSearchGateway spuSearchGateway;

    @Mock
    private KeywordTracker keywordTracker;

    @Mock
    private SpuSuggestGateway spuSuggestGateway;

    private SearchManager manager() {
        return new SearchManager(spuSearchGateway, keywordTracker, spuSuggestGateway);
    }

    @Test
    @DisplayName("search — SE1 全部过滤参数透传网关（brand/minPrice/maxPrice/sort/分页）")
    void shouldPassAllSe1ParamsToGateway_whenSearch() {
        manager().search("手机", 10L, "SoundMax", 100, 200, "price", 2, 10);

        ArgumentCaptor<SearchQuery> captor = ArgumentCaptor.forClass(SearchQuery.class);
        verify(spuSearchGateway).search(captor.capture());
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
        verify(spuSearchGateway).facets(captor.capture());
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
        verify(spuSearchGateway).search(captor.capture());
        assertEquals(SearchSortEnum.RELEVANCE, captor.getValue().sortOrDefault());
    }

    @Test
    @DisplayName("suggest — 热搜词前缀在前 + 商品名在后，去空格后委托双源")
    void shouldMergeHotWordsFirstThenProductNames_whenSuggest() {
        when(keywordTracker.getHotKeywordsByPrefix("手机", 10)).thenReturn(List.of("手机"));
        when(spuSuggestGateway.suggestNames("手机", 10)).thenReturn(List.of("手机壳", "蓝牙耳机"));

        List<String> suggestions = manager().suggest(" 手机 ");

        assertEquals(List.of("手机", "手机壳", "蓝牙耳机"), suggestions);
        verify(keywordTracker).getHotKeywordsByPrefix("手机", 10);
        verify(spuSuggestGateway).suggestNames("手机", 10);
    }

    @Test
    @DisplayName("suggest — 双源重复词只保留一次（精确去重）")
    void shouldDeduplicateAcrossSources_whenOverlap() {
        when(keywordTracker.getHotKeywordsByPrefix("手机", 10)).thenReturn(List.of("手机"));
        when(spuSuggestGateway.suggestNames("手机", 10)).thenReturn(List.of("手机", "手机壳"));

        assertEquals(List.of("手机", "手机壳"), manager().suggest("手机"));
    }

    @Test
    @DisplayName("suggest — 合并超 10 条截断为 10（热搜命中优先保留）")
    void shouldLimitToTen_whenMergedOverflow() {
        when(keywordTracker.getHotKeywordsByPrefix("机", 10))
                .thenReturn(List.of("热搜1", "热搜2", "热搜3", "热搜4", "热搜5", "热搜6"));
        when(spuSuggestGateway.suggestNames("机", 10))
                .thenReturn(List.of("商品1", "商品2", "商品3", "商品4", "商品5", "商品6"));

        List<String> suggestions = manager().suggest("机");

        assertEquals(10, suggestions.size());
        assertEquals("热搜1", suggestions.get(0));
        assertEquals("热搜6", suggestions.get(5));
        assertEquals("商品1", suggestions.get(6));
        assertEquals("商品4", suggestions.get(9));
    }

    @Test
    @DisplayName("suggest — keyword 缺失/空白 → 宽松返回空列表，双源均不触达")
    void shouldReturnEmptyWithoutSources_whenKeywordBlank() {
        assertTrue(manager().suggest(null).isEmpty());
        assertTrue(manager().suggest("   ").isEmpty());

        verifyNoInteractions(keywordTracker, spuSuggestGateway);
    }

    @Test
    @DisplayName("suggest — 超长前缀截断到 50 字符后照常联想（宽松防御）")
    void shouldTruncateOverlongPrefix_whenKeywordOverLimit() {
        String longKeyword = "机".repeat(60);
        String expectedPrefix = "机".repeat(50);
        when(keywordTracker.getHotKeywordsByPrefix(expectedPrefix, 10)).thenReturn(List.of());
        when(spuSuggestGateway.suggestNames(expectedPrefix, 10)).thenReturn(List.of("手机"));

        assertEquals(List.of("手机"), manager().suggest(longKeyword));
        verify(keywordTracker).getHotKeywordsByPrefix(expectedPrefix, 10);
        verify(spuSuggestGateway).suggestNames(expectedPrefix, 10);
    }

    @Test
    @DisplayName("suggest — 联想不记录热搜（浏览行为非检索行为，不污染热搜榜）")
    void shouldNotRecordHotKeyword_whenSuggest() {
        when(keywordTracker.getHotKeywordsByPrefix(any(), anyInt())).thenReturn(List.of());
        when(spuSuggestGateway.suggestNames(any(), anyInt())).thenReturn(List.of());

        manager().suggest("手机");

        verify(keywordTracker, never()).recordKeyword(any());
    }
}
