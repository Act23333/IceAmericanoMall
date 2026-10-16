package org.icedamericanomall.item.app;

import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.dto.SkuCO;
import org.icedamericanomall.item.adapter.request.StockOpReq;
import org.icedamericanomall.item.app.dto.SkuSpuDisplay;
import org.icedamericanomall.item.app.port.SkuSpuDisplayQuery;
import org.icedamericanomall.item.domain.enums.StockTypeEnum;
import org.icedamericanomall.item.domain.model.sku.Sku;
import org.icedamericanomall.item.domain.repository.SkuRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

/**
 * SkuManager 单元测试 —— 原子扣减/恢复编排（不限量分支、库存不足、幂等恢复）、最低价聚合，
 * 以及 I13 商品展示字段回填的批量（无 N+1）与 fail-open 口径。
 * （原 SkuServiceImplTest 语义迁移，ADR-020 原子 SQL 沉仓储后逻辑归本类编排。）
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SkuManager 编排单元测试")
class SkuManagerTest {

    @Mock
    private SkuRepository skuRepository;

    @Mock
    private SkuSpuDisplayQuery skuSpuDisplayQuery;

    private SkuManager skuManager;

    @BeforeEach
    void setUp() {
        skuManager = new SkuManager(skuRepository,
                new org.icedamericanomall.item.adapter.assembler.SkuConverterImpl(),
                skuSpuDisplayQuery);
    }

    private Sku sku(int stockType) {
        return Sku.builder().id(1L).skuId("SKU-1").spuId(10L)
                .price(1000).stock(50).stockType(stockType).status(1).build();
    }

    private StockOpReq op(Long skuId, int qty) {
        StockOpReq req = new StockOpReq();
        req.setSkuId(skuId);
        req.setQuantity(qty);
        return req;
    }

    // ==================== deductStock ====================

    @Test
    @DisplayName("deductStock — 限量 SKU：原子扣减成功（行数 1）")
    void shouldDeduct_whenLimitedStockAvailable() {
        when(skuRepository.findById(1L)).thenReturn(Optional.of(sku(StockTypeEnum.LIMITED.getCode())));
        when(skuRepository.deductStock(1L, 2)).thenReturn(1);

        skuManager.deductStock(1L, 2);

        verify(skuRepository).deductStock(1L, 2);
        verify(skuRepository, never()).increaseSoldCount(anyLong(), anyInt());
    }

    @Test
    @DisplayName("deductStock — 不限量 SKU：仅累加销量，不校验库存")
    void shouldIncreaseSoldCount_whenUnlimited() {
        when(skuRepository.findById(1L)).thenReturn(Optional.of(sku(StockTypeEnum.UNLIMITED.getCode())));
        when(skuRepository.increaseSoldCount(1L, 2)).thenReturn(1);

        skuManager.deductStock(1L, 2);

        verify(skuRepository).increaseSoldCount(1L, 2);
        verify(skuRepository, never()).deductStock(anyLong(), anyInt());
    }

    @Test
    @DisplayName("deductStock — 行数 0 → STOCK_INSUFFICIENT")
    void shouldThrowStockInsufficient_whenNoRowAffected() {
        when(skuRepository.findById(1L)).thenReturn(Optional.of(sku(StockTypeEnum.LIMITED.getCode())));
        when(skuRepository.deductStock(1L, 999)).thenReturn(0);

        BizException ex = assertThrows(BizException.class, () -> skuManager.deductStock(1L, 999));
        assertEquals(ErrorCode.STOCK_INSUFFICIENT, ex.getErrorCode());
    }

    @Test
    @DisplayName("deductStock — SKU 不存在 → SKU_NOT_FOUND")
    void shouldThrowNotFound_whenSkuMissing() {
        when(skuRepository.findById(1L)).thenReturn(Optional.empty());

        BizException ex = assertThrows(BizException.class, () -> skuManager.deductStock(1L, 2));
        assertEquals(ErrorCode.SKU_NOT_FOUND, ex.getErrorCode());
    }

    // ==================== restoreStock ====================

    @Test
    @DisplayName("restoreStock — 限量 SKU：原子恢复成功")
    void shouldRestore_whenLimited() {
        when(skuRepository.findById(1L)).thenReturn(Optional.of(sku(StockTypeEnum.LIMITED.getCode())));
        when(skuRepository.restoreStock(1L, 2)).thenReturn(1);

        skuManager.restoreStock(1L, 2);

        verify(skuRepository).restoreStock(1L, 2);
    }

    @Test
    @DisplayName("restoreStock — 不限量 SKU：仅回退销量")
    void shouldDecreaseSoldCount_whenUnlimited() {
        when(skuRepository.findById(1L)).thenReturn(Optional.of(sku(StockTypeEnum.UNLIMITED.getCode())));
        when(skuRepository.decreaseSoldCount(1L, 2)).thenReturn(1);

        skuManager.restoreStock(1L, 2);

        verify(skuRepository).decreaseSoldCount(1L, 2);
    }

    @Test
    @DisplayName("restoreStock — SKU 已删除：记 warn 后幂等返回（不抛异常）")
    void shouldBeIdempotent_whenSkuDeleted() {
        when(skuRepository.findById(1L)).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> skuManager.restoreStock(1L, 2));
        verify(skuRepository, never()).restoreStock(anyLong(), anyInt());
    }

    @Test
    @DisplayName("restoreStock — 原子 SQL 影响 0 行：幂等跳过不抛异常")
    void shouldBeIdempotent_whenRestoreAffectsNoRow() {
        when(skuRepository.findById(1L)).thenReturn(Optional.of(sku(StockTypeEnum.LIMITED.getCode())));
        when(skuRepository.restoreStock(1L, 2)).thenReturn(0);

        assertDoesNotThrow(() -> skuManager.restoreStock(1L, 2));
    }

    // ==================== 最低价聚合 ====================

    @Test
    @DisplayName("getMinPriceMapBySpuIds — 同商品取在售最低价")
    void shouldTakeMinPricePerSpu() {
        when(skuRepository.findOnSaleBySpuIds(List.of(10L))).thenReturn(List.of(
                Sku.builder().id(1L).spuId(10L).price(699900).status(1).build(),
                Sku.builder().id(2L).spuId(10L).price(599900).status(1).build()));

        Map<Long, Integer> map = skuManager.getMinPriceMapBySpuIds(List.of(10L));

        assertEquals(599900, map.get(10L));
    }

    @Test
    @DisplayName("getMinPriceMapBySpuIds — 入参为空返回空映射")
    void shouldReturnEmpty_whenSpuIdsEmpty() {
        assertTrue(skuManager.getMinPriceMapBySpuIds(List.of()).isEmpty());
    }

    // ==================== I13 商品展示字段回填 ====================

    @Test
    @DisplayName("getCosByIds — 整批一次回填商品四字段（无 N+1）")
    void shouldEnrichSpuFieldsInOneBatch_whenCosRequested() {
        when(skuRepository.findByIds(List.of(1L, 2L))).thenReturn(List.of(
                Sku.builder().id(1L).spuId(10L).price(1000).status(1).build(),
                Sku.builder().id(2L).spuId(11L).price(2000).status(1).build()));
        when(skuSpuDisplayQuery.findBySkuIds(anyCollection())).thenReturn(Map.of(
                1L, new SkuSpuDisplay(1L, "手工陶瓷茶杯", 5L, 10L, 77L),
                2L, new SkuSpuDisplay(2L, "青花瓷碗", 6L, 20L, null)));

        List<SkuCO> cos = skuManager.getCosByIds(List.of(1L, 2L));

        assertEquals("手工陶瓷茶杯", cos.get(0).getProductName());
        assertEquals(5L, cos.get(0).getSellerId());
        assertEquals(10L, cos.get(0).getCategoryId());
        assertEquals(77L, cos.get(0).getFreightTemplateId());
        assertEquals("青花瓷碗", cos.get(1).getProductName());
        assertNull(cos.get(1).getFreightTemplateId(), "未绑定运费模板 → null（不回落）");
        verify(skuSpuDisplayQuery, times(1)).findBySkuIds(anyCollection());
    }

    @Test
    @DisplayName("getCosByIds — 展示查询抛异常：四字段 null，SKU 本体照常返回（fail-open）")
    void shouldKeepSkuCos_whenDisplayQueryFails() {
        when(skuRepository.findByIds(List.of(1L))).thenReturn(List.of(
                Sku.builder().id(1L).spuId(10L).price(1000).stock(9).status(1).build()));
        when(skuSpuDisplayQuery.findBySkuIds(anyCollection()))
                .thenThrow(new IllegalStateException("spu 表不可用"));

        List<SkuCO> cos = assertDoesNotThrow(() -> skuManager.getCosByIds(List.of(1L)));

        assertEquals(1, cos.size());
        assertEquals(Integer.valueOf(1000), cos.get(0).getPrice());
        assertEquals(Integer.valueOf(9), cos.get(0).getStock());
        assertNull(cos.get(0).getProductName());
    }
}
