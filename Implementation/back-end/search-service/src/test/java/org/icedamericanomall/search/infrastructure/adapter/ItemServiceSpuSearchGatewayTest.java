package org.icedamericanomall.search.infrastructure.adapter;

import org.icedamericanomall.client.ItemSearchClient;
import org.icedamericanomall.dto.SpuSearchItemCO;
import org.icedamericanomall.dto.SpuSearchPageCO;
import org.icedamericanomall.dto.SpuSearchQueryCO;
import org.icedamericanomall.search.domain.model.search.SpuSearchHit;
import org.icedamericanomall.search.domain.model.search.SearchPage;
import org.icedamericanomall.search.domain.model.search.SearchQuery;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * item-service 检索网关测试（ADR-052）—— 覆盖：查询参数逐字段透传、
 * CO → 领域命中/分页映射、空响应降级、非 ES 能力方法（向量/facet/reindex）口径。
 *
 * <p>SQL 语义不在本测试职责内（已迁至 item 侧 {@code SpuSearchMapperH2Test} H2 集成测试）。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ItemServiceSpuSearchGateway — item 域检索委托")
class ItemServiceSpuSearchGatewayTest {

    @Mock
    private ItemSearchClient itemSearchClient;

    @InjectMocks
    private ItemServiceSpuSearchGateway gateway;

    private static SpuSearchItemCO item(long id) {
        SpuSearchItemCO co = new SpuSearchItemCO();
        co.setId(id);
        co.setSpuId("bbbbbbbb00007000800000000000000" + id);
        co.setCategoryId(10L);
        co.setName("无线蓝牙耳机" + id);
        co.setDescription("主动降噪");
        co.setBrand("SoundMax");
        co.setMainImage("img");
        co.setMinPrice(1999);
        co.setSoldCount(3);
        return co;
    }

    @Test
    @DisplayName("search — SE1 过滤/排序逐字段透传，CO 映射为领域命中（含最低价）")
    void shouldPassQueryAndMapHits_whenSearch() {
        SpuSearchPageCO pageCO = new SpuSearchPageCO();
        pageCO.setTotal(5L);
        pageCO.setRecords(List.of(item(1L)));
        when(itemSearchClient.searchPage(any())).thenReturn(pageCO);

        SearchPage<SpuSearchHit> result = gateway.search(
                new SearchQuery("耳机", null, 2, 10, "SoundMax", 10_000, 50_000, "price"));

        ArgumentCaptor<SpuSearchQueryCO> captor = ArgumentCaptor.forClass(SpuSearchQueryCO.class);
        verify(itemSearchClient).searchPage(captor.capture());
        SpuSearchQueryCO sent = captor.getValue();
        assertEquals("耳机", sent.getKeyword());
        assertEquals("SoundMax", sent.getBrand());
        assertEquals(10_000, sent.getMinPrice());
        assertEquals(50_000, sent.getMaxPrice());
        assertEquals("price", sent.getSort());
        assertEquals(2, sent.getPage());
        assertEquals(10, sent.getSize());

        assertEquals(5L, result.total());
        assertEquals(2, result.page());
        SpuSearchHit hit = result.records().get(0);
        assertEquals("bbbbbbbb000070008000000000000001", hit.spuId());
        assertEquals(1999, hit.price());
        assertEquals("无线蓝牙耳机1", hit.name());
    }

    @Test
    @DisplayName("search — item 端点降级/空响应 → 空页（fail-closed 口径）")
    void shouldReturnEmptyPage_whenClientReturnsNull() {
        when(itemSearchClient.searchPage(any())).thenReturn(null);

        SearchPage<SpuSearchHit> result = gateway.search(new SearchQuery("耳机", null, 1, 10));

        assertTrue(result.records().isEmpty());
        assertEquals(0L, result.total());
        assertEquals(1, result.page());
    }

    @Test
    @DisplayName("非 ES 能力方法 — facets 空对象 / vectorSearch 空列表 / reindex 未启用标记 / findVectorById 恒 empty")
    void shouldKeepNonEsCapabilitiesEmpty() {
        assertTrue(gateway.facets(new SearchQuery("耳机", null, 1, 10)).brands().isEmpty());
        assertTrue(gateway.vectorSearch(new double[]{0.1}, 5).isEmpty());
        assertEquals(false, gateway.reindex().get("esEnabled"));
        assertTrue(gateway.findVectorById("0f1e2d3c4b5a69788796a5b4c3d2e1f0").isEmpty());

        gateway.upsertVectorDoc(null);
        verify(itemSearchClient, never()).searchPage(any());
    }
}
