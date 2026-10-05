package org.icedamericanomall.merchant.infrastructure.persistence.shop;

import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.merchant.domain.model.shop.ShopDecoration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 店铺装修仓储实现单元测试 —— JSON 字符串列互转收口与 uk_shop UPSERT
 * （首次保存插入 / 再次保存整体替换，单语句无分支）。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ShopDecorationRepositoryImpl 仓储单元测试")
class ShopDecorationRepositoryImplTest {

    @Mock
    private ShopDecorationMapper shopDecorationMapper;

    @InjectMocks
    private ShopDecorationRepositoryImpl shopDecorationRepository;

    @Test
    @DisplayName("save — 落库为 upsert 单语句：JSON 列按数组序列化（首次插入路径）")
    void shouldUpsertWithJsonColumns_whenFirstSave() {
        ShopDecoration decoration = ShopDecoration.of(5L, "欢迎光临",
                List.of("https://a.png", "https://b.png"), List.of(11L, 22L, 11L));

        shopDecorationRepository.save(decoration);

        ArgumentCaptor<ShopDecorationPO> captor = ArgumentCaptor.forClass(ShopDecorationPO.class);
        verify(shopDecorationMapper).upsert(captor.capture());
        ShopDecorationPO saved = captor.getValue();
        assertEquals(5L, saved.getShopId());
        assertEquals("欢迎光临", saved.getAnnouncement());
        assertEquals("[\"https://a.png\",\"https://b.png\"]", saved.getBanners());
        assertEquals("[11,22]", saved.getHotProductIds(), "领域层已去重，序列化不再重复");
    }

    @Test
    @DisplayName("save — 再次保存整体替换：仍是同一 UPSERT 语句，写入新值")
    void shouldUpsertAgainWithLatestValues_whenSavedTwice() {
        shopDecorationRepository.save(ShopDecoration.of(5L, "第一版", List.of("https://old.png"), null));
        shopDecorationRepository.save(ShopDecoration.of(5L, "第二版",
                List.of("https://new1.png", "https://new2.png"), List.of(9L)));

        ArgumentCaptor<ShopDecorationPO> captor = ArgumentCaptor.forClass(ShopDecorationPO.class);
        verify(shopDecorationMapper, times(2)).upsert(captor.capture());
        ShopDecorationPO second = captor.getAllValues().get(1);
        assertEquals("第二版", second.getAnnouncement());
        assertEquals("[\"https://new1.png\",\"https://new2.png\"]", second.getBanners());
        assertEquals("[9]", second.getHotProductIds());
    }

    @Test
    @DisplayName("save — 空列表序列化为 NULL（清空语义）")
    void shouldWriteNullJsonColumns_whenDecorationEmpty() {
        shopDecorationRepository.save(ShopDecoration.of(5L, null, List.of(), List.of()));

        ArgumentCaptor<ShopDecorationPO> captor = ArgumentCaptor.forClass(ShopDecorationPO.class);
        verify(shopDecorationMapper).upsert(captor.capture());
        assertNull(captor.getValue().getBanners());
        assertNull(captor.getValue().getHotProductIds());
    }

    @Test
    @DisplayName("findByShopId — JSON 列解析回 List（轮播字符串 / 推荐位 Long）")
    void shouldParseJsonToDomain_whenDecorationExists() {
        ShopDecorationPO dataObject = new ShopDecorationPO();
        dataObject.setShopId(5L);
        dataObject.setAnnouncement("欢迎光临");
        dataObject.setBanners("[\"https://a.png\",\"https://b.png\"]");
        dataObject.setHotProductIds("[11,22]");
        when(shopDecorationMapper.selectOne(any())).thenReturn(dataObject);

        Optional<ShopDecoration> found = shopDecorationRepository.findByShopId(5L);

        assertTrue(found.isPresent());
        assertEquals("欢迎光临", found.get().getAnnouncement());
        assertEquals(List.of("https://a.png", "https://b.png"), found.get().getBanners());
        assertEquals(List.of(11L, 22L), found.get().getHotProductIds());
    }

    @Test
    @DisplayName("findByShopId — 未装修（查无记录）返回 empty")
    void shouldReturnEmpty_whenDecorationAbsent() {
        when(shopDecorationMapper.selectOne(any())).thenReturn(null);

        assertTrue(shopDecorationRepository.findByShopId(5L).isEmpty());
    }

    @Test
    @DisplayName("findByShopId — shopId 为空直接返回 empty，不触库")
    void shouldReturnEmpty_whenShopIdNull() {
        assertTrue(shopDecorationRepository.findByShopId(null).isEmpty());

        verify(shopDecorationMapper, times(0)).selectOne(any());
    }

    @Test
    @DisplayName("findByShopId — 轮播 JSON 列损坏 → PARAM_ERROR（快败不静默）")
    void shouldThrowParamError_whenBannerJsonCorrupted() {
        ShopDecorationPO dataObject = new ShopDecorationPO();
        dataObject.setShopId(5L);
        dataObject.setBanners("{not-a-json-array");
        when(shopDecorationMapper.selectOne(any())).thenReturn(dataObject);

        BizException ex = assertThrows(BizException.class,
                () -> shopDecorationRepository.findByShopId(5L));

        assertEquals(ErrorCode.PARAM_ERROR, ex.getErrorCode());
    }
}
