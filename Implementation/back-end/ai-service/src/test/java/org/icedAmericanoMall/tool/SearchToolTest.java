package org.icedAmericanoMall.tool;

import org.icedAmericanoMall.client.SearchClient;
import org.icedAmericanoMall.dto.ProductSearchResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.noLazy.common.domain.Result;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SearchToolTest {

    @Mock
    private SearchClient searchClient;

    private SearchTool searchTool;

    @BeforeEach
    void setUp() {
        searchTool = new SearchTool(searchClient);
    }

    @Test
    @DisplayName("搜索 → 返回格式化商品列表")
    void shouldReturnFormattedResults_whenProductsFound() {
        Map<String, Object> record = new java.util.LinkedHashMap<>();
        record.put("name", "Nike ZoomX");
        record.put("productId", "P001");
        record.put("price", 49900);
        record.put("soldCount", 1523);
        when(searchClient.searchProducts(eq("跑鞋"), anyInt())).thenReturn(
                Result.ok(Map.of("records", List.of(record))));

        String result = searchTool.searchProducts("跑鞋");
        assertTrue(result.contains("Nike ZoomX"));
        assertTrue(result.contains("P001"));
        assertTrue(result.contains("1523"));
    }

    @Test
    @DisplayName("搜索 → 无结果时返回友好提示")
    void shouldReturnFriendlyMessage_whenNoResults() {
        when(searchClient.searchProducts(eq("xyz"), anyInt())).thenReturn(
                Result.ok(Map.of("records", List.of())));

        String result = searchTool.searchProducts("xyz");
        assertTrue(result.contains("未找到") || result.contains("换个关键词"));
    }
}
