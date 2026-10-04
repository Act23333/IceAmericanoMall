package org.icedamericanomall.ai.infrastructure.adapter.tool;

import org.icedamericanomall.ai.domain.model.product.ProductHit;
import org.icedamericanomall.ai.domain.port.SearchPort;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * SearchTool 单元测试 —— 只依赖领域端口 SearchPort。
 */
@ExtendWith(MockitoExtension.class)
class SearchToolTest {

    @Mock
    private SearchPort searchPort;

    private SearchTool searchTool;

    @BeforeEach
    void setUp() {
        searchTool = new SearchTool(searchPort);
    }

    @Test
    @DisplayName("搜索 → 返回格式化商品列表")
    void shouldReturnFormattedResults_whenProductsFound() {
        when(searchPort.searchProducts(eq("跑鞋"), anyInt())).thenReturn(List.of(
                new ProductHit("P001", "Nike ZoomX", "轻量跑鞋", 49900, 1523)));

        String result = searchTool.searchProducts("跑鞋");
        assertTrue(result.contains("Nike ZoomX"));
        assertTrue(result.contains("P001"));
        assertTrue(result.contains("1523"));
        assertTrue(result.contains("499.00"));
    }

    @Test
    @DisplayName("搜索 → 无结果时返回友好提示")
    void shouldReturnFriendlyMessage_whenNoResults() {
        when(searchPort.searchProducts(eq("xyz"), anyInt())).thenReturn(List.of());

        String result = searchTool.searchProducts("xyz");
        assertTrue(result.contains("未找到") || result.contains("换个关键词"));
    }

    @Test
    @DisplayName("搜索 → 搜索服务不可用时返回兜底文案")
    void shouldReturnUnavailableMessage_whenSearchServiceDown() {
        when(searchPort.searchProducts(eq("跑鞋"), anyInt()))
                .thenThrow(new BizException(ErrorCode.AI_SERVICE_UNAVAILABLE, "搜索服务暂不可用"));

        assertTrue(searchTool.searchProducts("跑鞋").contains("暂不可用"));
    }
}
