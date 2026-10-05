package org.icedamericanomall.search.adapter.web;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.icedamericanomall.common.api.Result;
import org.icedamericanomall.search.adapter.assembler.ProductSearchConverter;
import org.icedamericanomall.search.adapter.request.SearchPageReq;
import org.icedamericanomall.search.adapter.response.ProductSearchVO;
import org.icedamericanomall.search.adapter.response.SearchFacetsVO;
import org.icedamericanomall.search.app.SearchManager;
import org.icedamericanomall.search.domain.model.search.SearchFacets;
import org.icedamericanomall.search.domain.model.search.SearchPage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SearchController 单元测试 —— SE1 新增过滤参数透传与 SE6 facets 薄委托（控制器不做业务逻辑）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("SearchController — SE1 参数透传 + SE6 facets 薄委托")
class SearchControllerTest {

    @Mock
    private SearchManager searchManager;

    @Mock
    private ProductSearchConverter converter;

    private SearchController controller() {
        return new SearchController(searchManager, converter);
    }

    @Test
    @DisplayName("search — brand/minPrice/maxPrice/sort 全量透传 app 层")
    void shouldPassSe1FilterParams_whenSearch() {
        SearchPageReq req = new SearchPageReq();
        req.setKeyword("手机");
        req.setCategoryId(10L);
        req.setBrand("Apple");
        req.setMinPrice(100);
        req.setMaxPrice(200);
        req.setSort("newest");
        req.setPage(2);
        req.setSize(5);

        Page<ProductSearchVO> voPage = new Page<>(2, 5);
        when(searchManager.search("手机", 10L, "Apple", 100, 200, "newest", 2, 5))
                .thenReturn(SearchPage.empty(2, 5));
        when(converter.toPage(any())).thenReturn(voPage);

        Result<Page<ProductSearchVO>> result = controller().search(req);

        verify(searchManager).search("手机", 10L, "Apple", 100, 200, "newest", 2, 5);
        assertSame(voPage, result.getData());
    }

    @Test
    @DisplayName("facets — 薄委托 getFacets（仅 keyword/categoryId），域 facet 转 VO")
    void shouldDelegateFacets_whenFacetsEndpoint() {
        SearchPageReq req = new SearchPageReq();
        req.setKeyword("手机");
        req.setCategoryId(10L);

        SearchFacets facets = SearchFacets.empty();
        SearchFacetsVO vo = new SearchFacetsVO();
        when(searchManager.getFacets("手机", 10L)).thenReturn(facets);
        when(converter.toVO(facets)).thenReturn(vo);

        Result<SearchFacetsVO> result = controller().facets(req);

        verify(searchManager).getFacets("手机", 10L);
        assertSame(vo, result.getData());
    }

    @Test
    @DisplayName("facets — DB 链空对象照常返回（brands/priceRanges 空列表，不报错）")
    void shouldReturnEmptyFacetsVo_whenDomainFacetsEmpty() {
        SearchPageReq req = new SearchPageReq();
        SearchFacetsVO vo = new SearchFacetsVO();
        vo.setBrands(java.util.List.of());
        vo.setPriceRanges(java.util.List.of());
        when(searchManager.getFacets(null, null)).thenReturn(SearchFacets.empty());
        when(converter.toVO(any())).thenReturn(vo);

        Result<SearchFacetsVO> result = controller().facets(req);

        assertEquals(java.util.List.of(), result.getData().getBrands());
        assertEquals(java.util.List.of(), result.getData().getPriceRanges());
    }

    @Test
    @DisplayName("suggest — SE5 薄委托 searchManager.suggest（合并/去重/截断在 app 层）")
    void shouldDelegateSuggest_whenKeywordPresent() {
        List<String> suggestions = List.of("手机", "手机壳");
        when(searchManager.suggest("手机")).thenReturn(suggestions);

        Result<List<String>> result = controller().suggest("手机");

        verify(searchManager).suggest("手机");
        assertSame(suggestions, result.getData());
    }

    @Test
    @DisplayName("suggest — keyword 缺失 → 宽松委托（app 层返回空列表，不报 400）")
    void shouldDelegateNullKeyword_whenSuggestWithoutKeyword() {
        when(searchManager.suggest(null)).thenReturn(List.of());

        Result<List<String>> result = controller().suggest(null);

        verify(searchManager).suggest(null);
        assertEquals(List.of(), result.getData());
    }
}
