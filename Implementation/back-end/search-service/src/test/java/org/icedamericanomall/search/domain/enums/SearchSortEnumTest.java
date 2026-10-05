package org.icedamericanomall.search.domain.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link SearchSortEnum} 单元测试 —— 契约取值映射与宽松回落（SE1）。
 */
@DisplayName("SearchSortEnum — 契约取值解析与宽松回落")
class SearchSortEnumTest {

    @Test
    @DisplayName("解析 — 四个契约取值逐一映射（大小写不敏感）")
    void shouldParseContractValues() {
        assertEquals(SearchSortEnum.SALES, SearchSortEnum.fromValue("sales"));
        assertEquals(SearchSortEnum.PRICE, SearchSortEnum.fromValue("PRICE"));
        assertEquals(SearchSortEnum.NEWEST, SearchSortEnum.fromValue("Newest"));
        assertEquals(SearchSortEnum.RELEVANCE, SearchSortEnum.fromValue("relevance"));
    }

    @Test
    @DisplayName("回落 — null/空白/未知取值一律 relevance（默认相关度即现行为）")
    void shouldFallbackRelevance_whenNullOrUnknown() {
        assertEquals(SearchSortEnum.RELEVANCE, SearchSortEnum.fromValue(null));
        assertEquals(SearchSortEnum.RELEVANCE, SearchSortEnum.fromValue(" "));
        assertEquals(SearchSortEnum.RELEVANCE, SearchSortEnum.fromValue("bogus"));
    }
}
