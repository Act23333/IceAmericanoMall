package org.icedamericanomall.item.app;

import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.item.adapter.request.StockOpReq;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

/**
 * SkuManager 单元测试 —— 原子扣减/恢复编排（不限量分支、库存不足、幂等恢复）与最低价聚合。
 * （原 SkuServiceImplTest 语义迁移，ADR-020 原子 SQL 沉仓储后逻辑归本类编排。）
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SkuManager 编排单元测试")
class SkuManagerTest {

    @Mock
    private SkuRepository skuRepository;

    private SkuManager skuManager;

    @BeforeEach
    void setUp() {
        skuManager = new SkuManager(skuRepository,
                new org.icedamericanomall.item.adapter.assembler.SkuConverterImpl());
    }

    private Sku sku(int stockType) {
        return Sku.builder().id(1L).skuId("SKU-1").productId(10L)
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
    @DisplayName("getMinPriceMapByProductIds — 同商品取在售最低价")
    void shouldTakeMinPricePerProduct() {
        when(skuRepository.findOnSaleByProductIds(List.of(10L))).thenReturn(List.of(
                Sku.builder().id(1L).productId(10L).price(699900).status(1).build(),
                Sku.builder().id(2L).productId(10L).price(599900).status(1).build()));

        Map<Long, Integer> map = skuManager.getMinPriceMapByProductIds(List.of(10L));

        assertEquals(599900, map.get(10L));
    }

    @Test
    @DisplayName("getMinPriceMapByProductIds — 入参为空返回空映射")
    void shouldReturnEmpty_whenProductIdsEmpty() {
        assertTrue(skuManager.getMinPriceMapByProductIds(List.of()).isEmpty());
    }
}
