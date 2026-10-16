package org.icedamericanomall.item.app.search;

import org.icedamericanomall.dto.SpuSearchItemCO;
import org.icedamericanomall.dto.SpuSearchPageCO;
import org.icedamericanomall.dto.SpuSearchQueryCO;
import org.icedamericanomall.item.domain.model.PageResult;
import org.icedamericanomall.item.domain.model.spu.SpuSearchCard;
import org.icedamericanomall.item.domain.model.spu.SpuSearchCriteria;
import org.icedamericanomall.item.domain.repository.SpuRepository;
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
import static org.mockito.ArgumentMatchers.anyLong;
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
    private SpuRepository spuRepository;

    @InjectMocks
    private ItemSearchQueryManager manager;

    /** 32-hex 业务键（{@code spu.spu_id} 线格式；长度必须为 32，ADR-050）。 */
    private static String businessKey(long id) {
        return String.format("aaaa0000aaaa0000aaaa0000%08x", id);
    }

    private static SpuSearchCard card(long id) {
        return new SpuSearchCard(id, businessKey(id), 10L,
                "商品" + id, "描述", "SoundMax", "img", 1999, 3, 1_767_225_600_000L);
    }

    @Test
    @DisplayName("searchPage — 卡片映射为 CO（hex 透传/价格/时间），total 透传")
    void shouldMapCardsToCO_whenSearchPage() {
        when(spuRepository.searchPage(any())).thenReturn(
                new PageResult<>(List.of(card(1L), card(2L)), 7L, 1, 10));

        SpuSearchQueryCO query = new SpuSearchQueryCO();
        query.setKeyword("耳机");
        SpuSearchPageCO result = manager.searchPage(query);

        assertEquals(7L, result.getTotal());
        assertEquals(2, result.getRecords().size());
        SpuSearchItemCO first = result.getRecords().get(0);
        assertEquals(businessKey(1L), first.getSpuId());
        assertEquals(1999, first.getMinPrice());
        assertEquals(1_767_225_600_000L, first.getPublishTime());
    }

    @Test
    @DisplayName("searchPage — 页码/页长防御性夹取（0→1，超限→100；排序词汇透传）")
    void shouldClampPaging_whenSearchPage() {
        when(spuRepository.searchPage(any())).thenReturn(PageResult.empty(1, 100));
        SpuSearchQueryCO query = new SpuSearchQueryCO();
        query.setPage(0);
        query.setSize(9999);
        query.setSort("price");

        manager.searchPage(query);

        ArgumentCaptor<SpuSearchCriteria> captor = ArgumentCaptor.forClass(SpuSearchCriteria.class);
        verify(spuRepository).searchPage(captor.capture());
        assertEquals(1, captor.getValue().page());
        assertEquals(100, captor.getValue().size());
        assertEquals("price", captor.getValue().sort());
    }

    @Test
    @DisplayName("exportPage — 页长夹取后透传（ES 重建数据源）")
    void shouldClampSize_whenExportPage() {
        when(spuRepository.exportPage(anyInt(), anyInt())).thenReturn(PageResult.empty(1, 100));

        manager.exportPage(0, 5000);

        verify(spuRepository).exportPage(1, 100);
    }

    @Test
    @DisplayName("suggest — 空/空白前缀不发起查询；trim 与条数上限（50）生效")
    void shouldGuardSuggestInput() {
        assertTrue(manager.suggest(null, 10).isEmpty());
        assertTrue(manager.suggest("  ", 10).isEmpty());
        verify(spuRepository, never()).suggestNames(anyString(), anyInt());

        when(spuRepository.suggestNames("耳机", 50)).thenReturn(List.of("无线耳机"));
        assertEquals(List.of("无线耳机"), manager.suggest(" 耳机 ", 999));
    }

    @Test
    @DisplayName("detail — 空白入参返回 null；合法业务键未命中（仓储缺失）同样返回 null")
    void shouldReturnNull_whenDetailMissing() {
        assertNull(manager.detail("  "));
        assertNull(manager.detail(null));

        String key = businessKey(9L);
        when(spuRepository.findSearchCardByBusinessId(key)).thenReturn(null);
        assertNull(manager.detail(key));
    }

    @Test
    @DisplayName("detail — 单形态：只按 SPU 业务键解析（技术键/纯数字分支已废止，ADR-062 §决策 4）")
    void shouldResolveByBusinessKeyOnly() {
        String key = businessKey(1L);
        when(spuRepository.findSearchCardByBusinessId(key)).thenReturn(card(1L));

        assertEquals(key, manager.detail(key).getSpuId());
        // 原「纯数字 = 技术键」分支已删除：数字入参只会被当成业务键字符串去查（不落技术键解析）
        when(spuRepository.findSearchCardByBusinessId("9")).thenReturn(null);
        assertNull(manager.detail("9"));

        when(spuRepository.findSearchCardByBusinessId("not-a-hex-key")).thenReturn(null);
        assertNull(manager.detail("not-a-hex-key"));
    }
}
