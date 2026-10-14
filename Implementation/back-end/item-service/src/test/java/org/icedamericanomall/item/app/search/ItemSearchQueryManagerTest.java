package org.icedamericanomall.item.app.search;

import org.icedamericanomall.dto.ProductSearchItemCO;
import org.icedamericanomall.dto.ProductSearchPageCO;
import org.icedamericanomall.dto.ProductSearchQueryCO;
import org.icedamericanomall.item.domain.model.PageResult;
import org.icedamericanomall.item.domain.model.product.ProductSearchCard;
import org.icedamericanomall.item.domain.model.product.ProductSearchCriteria;
import org.icedamericanomall.item.domain.repository.ProductRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 商品检索出口编排测试（ADR-052）—— 覆盖：入参夹取（页码/页长/联想条数）、
 * 卡片→CO 映射（业务 ID hex 透传、minPrice/publishTime）、detail 缺失语义。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ItemSearchQueryManager — 检索出口编排")
class ItemSearchQueryManagerTest {

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private ItemSearchQueryManager manager;

    private static ProductSearchCard card(long id) {
        return new ProductSearchCard(id, "aaaa0000aaaa0000aaaa0000aaaa000" + id, 10L,
                "商品" + id, "描述", "SoundMax", "img", 1999, 3, 1_767_225_600_000L);
    }

    @Test
    @DisplayName("searchPage — 卡片映射为 CO（hex 透传/价格/时间），total 透传")
    void shouldMapCardsToCO_whenSearchPage() {
        when(productRepository.searchPage(any())).thenReturn(
                new PageResult<>(List.of(card(1L), card(2L)), 7L, 1, 10));

        ProductSearchQueryCO query = new ProductSearchQueryCO();
        query.setKeyword("耳机");
        ProductSearchPageCO result = manager.searchPage(query);

        assertEquals(7L, result.getTotal());
        assertEquals(2, result.getRecords().size());
        ProductSearchItemCO first = result.getRecords().get(0);
        assertEquals("aaaa0000aaaa0000aaaa0000aaaa0001", first.getProductId());
        assertEquals(1999, first.getMinPrice());
        assertEquals(1_767_225_600_000L, first.getPublishTime());
    }

    @Test
    @DisplayName("searchPage — 页码/页长防御性夹取（0→1，超限→100；排序词汇透传）")
    void shouldClampPaging_whenSearchPage() {
        when(productRepository.searchPage(any())).thenReturn(PageResult.empty(1, 100));
        ProductSearchQueryCO query = new ProductSearchQueryCO();
        query.setPage(0);
        query.setSize(9999);
        query.setSort("price");

        manager.searchPage(query);

        ArgumentCaptor<ProductSearchCriteria> captor = ArgumentCaptor.forClass(ProductSearchCriteria.class);
        verify(productRepository).searchPage(captor.capture());
        assertEquals(1, captor.getValue().page());
        assertEquals(100, captor.getValue().size());
        assertEquals("price", captor.getValue().sort());
    }

    @Test
    @DisplayName("exportPage — 页长夹取后透传（ES 重建数据源）")
    void shouldClampSize_whenExportPage() {
        when(productRepository.exportPage(anyInt(), anyInt())).thenReturn(PageResult.empty(1, 100));

        manager.exportPage(0, 5000);

        verify(productRepository).exportPage(1, 100);
    }

    @Test
    @DisplayName("suggest — 空/空白前缀不发起查询；trim 与条数上限（50）生效")
    void shouldGuardSuggestInput() {
        assertTrue(manager.suggest(null, 10).isEmpty());
        assertTrue(manager.suggest("  ", 10).isEmpty());
        verify(productRepository, never()).suggestNames(anyString(), anyInt());

        when(productRepository.suggestNames("耳机", 50)).thenReturn(List.of("无线耳机"));
        assertEquals(List.of("无线耳机"), manager.suggest(" 耳机 ", 999));
    }

    @Test
    @DisplayName("detail — 仓储缺失返回 null；命中映射为 CO")
    void shouldReturnNull_whenDetailMissing() {
        assertNull(manager.detail(null));
        when(productRepository.findSearchCardById(9L)).thenReturn(null);
        assertNull(manager.detail(9L));

        when(productRepository.findSearchCardById(1L)).thenReturn(card(1L));
        assertEquals("aaaa0000aaaa0000aaaa0000aaaa0001", manager.detail(1L).getProductId());
    }
}
