package org.icedamericanomall.search.infrastructure.adapter;

import org.icedamericanomall.client.ItemSearchClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 商品名联想网关测试（ADR-052）—— 薄委托 + null 防御（SQL 语义已迁 item 侧 H2 测试）。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ItemServiceSpuSuggestGateway — item 域联想委托")
class ItemServiceSpuSuggestGatewayTest {

    @Mock
    private ItemSearchClient itemSearchClient;

    @InjectMocks
    private ItemServiceSpuSuggestGateway gateway;

    @Test
    @DisplayName("suggestNames — 参数透传，结果原样返回")
    void shouldDelegateSuggest() {
        when(itemSearchClient.suggest("iPhone", 10)).thenReturn(List.of("iPhone 15 Pro", "iPhone 15 手机"));

        List<String> names = gateway.suggestNames("iPhone", 10);

        verify(itemSearchClient).suggest("iPhone", 10);
        assertEquals(List.of("iPhone 15 Pro", "iPhone 15 手机"), names);
    }

    @Test
    @DisplayName("suggestNames — 降级/空响应 → 空列表（联想位可空，不阻断输入框）")
    void shouldReturnEmpty_whenClientReturnsNull() {
        when(itemSearchClient.suggest("iPhone", 10)).thenReturn(null);

        assertTrue(gateway.suggestNames("iPhone", 10).isEmpty());
    }
}
