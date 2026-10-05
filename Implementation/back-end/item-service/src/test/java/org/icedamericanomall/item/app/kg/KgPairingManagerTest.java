package org.icedamericanomall.item.app.kg;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.icedamericanomall.client.KgClient;
import org.icedamericanomall.dto.KgNeighborCO;
import org.icedamericanomall.item.adapter.response.PairingCardVO;
import org.icedamericanomall.item.adapter.response.RecommendProductVO;
import org.icedamericanomall.item.app.ProductManager;
import org.icedamericanomall.item.app.SkuManager;
import org.icedamericanomall.item.domain.enums.ProductStatusEnum;
import org.icedamericanomall.item.domain.model.kg.KgSchema;
import org.icedamericanomall.item.domain.model.product.Product;
import org.icedamericanomall.item.domain.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * KgPairingManager 单元测试 —— P35 搭配卡（KG 命中组装与排序 / 排除自身 / 过滤下架 /
 * FALLBACK 回退 / NONE 空态 / 全链路 fail-open / limit 边界）。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("KgPairingManager — P35 搭配推荐卡组装")
class KgPairingManagerTest {

    private static final Long ANCHOR_ID = 1L;
    private static final Long ANCHOR_CATEGORY = 10L;

    @Mock
    private KgClient kgClient;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private SkuManager skuManager;

    @Mock
    private ProductManager productManager;

    private KgPairingManager kgPairingManager;

    @BeforeEach
    void setUp() {
        kgPairingManager = new KgPairingManager(kgClient, productRepository, skuManager, productManager);
    }

    private Product product(Long id, Long categoryId, int soldCount, ProductStatusEnum status) {
        Product product = Product.publish(100L, categoryId, null, "商品" + id, "img" + id + ".jpg",
                "描述", "品牌A");
        product.setId(id);
        product.setSoldCount(soldCount);
        product.setStatus(status.getCode());
        return product;
    }

    private void stubAnchor() {
        when(productRepository.findById(ANCHOR_ID))
                .thenReturn(product(ANCHOR_ID, ANCHOR_CATEGORY, 50, ProductStatusEnum.ON_SHELF));
    }

    private KgNeighborCO neighbor(Long dstCategoryId, int weight) {
        return new KgNeighborCO(KgSchema.NODE_CATEGORY, dstCategoryId, KgSchema.REL_PAIR_WITH, weight, 1.0f);
    }

    private Page<Product> pageOf(Product... products) {
        Page<Product> page = new Page<>(1, 6);
        page.setRecords(List.of(products));
        return page;
    }

    // ==================== KG 命中（主召回） ====================

    @Test
    @DisplayName("KG 命中 — 按规则权重降序排序（weight=10 类目商品先于 weight=5），排除锚点自身")
    void shouldOrderByRuleWeight_andExcludeSelf_whenGraphHits() {
        stubAnchor();
        when(kgClient.neighbors(KgSchema.NODE_CATEGORY, ANCHOR_CATEGORY,
                KgSchema.REL_PAIR_WITH, KgSchema.NODE_CATEGORY, 1, null))
                .thenReturn(List.of(neighbor(20L, 5), neighbor(30L, 10)));
        when(productRepository.pageOnSale(null, 20L, null, "sales", 1, 6))
                .thenReturn(pageOf(product(ANCHOR_ID, 20L, 999, ProductStatusEnum.ON_SHELF),
                        product(2L, 20L, 100, ProductStatusEnum.ON_SHELF)));
        when(productRepository.pageOnSale(null, 30L, null, "sales", 1, 6))
                .thenReturn(pageOf(product(3L, 30L, 1, ProductStatusEnum.ON_SHELF)));
        when(skuManager.getMinPriceMapByProductIds(any())).thenReturn(Map.of(2L, 1200, 3L, 3400));

        PairingCardVO card = kgPairingManager.pairing(ANCHOR_ID, null);

        assertEquals(PairingCardVO.SOURCE_KG, card.getSource());
        assertEquals(List.of(3L, 2L),
                card.getItems().stream().map(RecommendProductVO::getProductId).toList());
        assertEquals(3400, card.getItems().get(0).getPrice());
        assertEquals(1200, card.getItems().get(1).getPrice());
    }

    @Test
    @DisplayName("KG 命中 — 同权重按销量降序；候选中下架商品被过滤（P27 同款防御口径）")
    void shouldOrderBySalesAndFilterOffShelf_withinSameWeight() {
        stubAnchor();
        when(kgClient.neighbors(any(), any(), any(), any(), anyInt(), any()))
                .thenReturn(List.of(neighbor(20L, 0)));
        when(productRepository.pageOnSale(null, 20L, null, "sales", 1, 6))
                .thenReturn(pageOf(
                        product(2L, 20L, 300, ProductStatusEnum.ON_SHELF),
                        product(3L, 20L, 999, ProductStatusEnum.OFF_SHELF),
                        product(4L, 20L, 100, ProductStatusEnum.ON_SHELF)));
        when(skuManager.getMinPriceMapByProductIds(any())).thenReturn(Map.of());

        PairingCardVO card = kgPairingManager.pairing(ANCHOR_ID, null);

        assertEquals(PairingCardVO.SOURCE_KG, card.getSource());
        assertEquals(List.of(2L, 4L),
                card.getItems().stream().map(RecommendProductVO::getProductId).toList());
    }

    @Test
    @DisplayName("KG 命中 — limit 截断与归一（邻居多类目候选 → 最多 limit 张卡）")
    void shouldCapItemsAtLimit() {
        stubAnchor();
        when(kgClient.neighbors(any(), any(), any(), any(), anyInt(), any()))
                .thenReturn(List.of(neighbor(20L, 0)));
        when(productRepository.pageOnSale(eq(null), eq(20L), eq(null), eq("sales"), eq(1), eq(3)))
                .thenReturn(pageOf(
                        product(2L, 20L, 300, ProductStatusEnum.ON_SHELF),
                        product(3L, 20L, 200, ProductStatusEnum.ON_SHELF),
                        product(4L, 20L, 100, ProductStatusEnum.ON_SHELF)));
        when(skuManager.getMinPriceMapByProductIds(any())).thenReturn(Map.of());

        PairingCardVO card = kgPairingManager.pairing(ANCHOR_ID, 3);

        assertEquals(3, card.getItems().size());
    }

    @Test
    @DisplayName("KG 命中 — 非类目 dstType 邻居被跳过（Phase 1 仅消费 category 邻居）")
    void shouldSkipNonCategoryNeighbors() {
        stubAnchor();
        when(kgClient.neighbors(any(), any(), any(), any(), anyInt(), any()))
                .thenReturn(List.of(new KgNeighborCO(KgSchema.NODE_PRODUCT, 99L, "PAIR_WITH", 0, 1.0f),
                        neighbor(20L, 0)));
        when(productRepository.pageOnSale(null, 20L, null, "sales", 1, 6))
                .thenReturn(pageOf(product(2L, 20L, 300, ProductStatusEnum.ON_SHELF)));
        when(skuManager.getMinPriceMapByProductIds(any())).thenReturn(Map.of(2L, 900));

        PairingCardVO card = kgPairingManager.pairing(ANCHOR_ID, null);

        assertEquals(PairingCardVO.SOURCE_KG, card.getSource());
        assertEquals(1, card.getItems().size());
        verify(productRepository, never()).pageOnSale(any(), eq(99L), any(), any(), anyInt(), anyInt());
    }

    // ==================== FALLBACK / NONE ====================

    @Test
    @DisplayName("无规则命中 — 回退 P27 向量相似管道（source=FALLBACK）")
    void shouldFallbackToVector_whenNoRuleHit() {
        stubAnchor();
        when(kgClient.neighbors(any(), any(), any(), any(), anyInt(), any()))
                .thenReturn(List.of());
        RecommendProductVO similar = new RecommendProductVO();
        similar.setProductId(9L);
        when(productManager.similarProducts(ANCHOR_ID, 6)).thenReturn(List.of(similar));

        PairingCardVO card = kgPairingManager.pairing(ANCHOR_ID, null);

        assertEquals(PairingCardVO.SOURCE_FALLBACK, card.getSource());
        assertEquals(1, card.getItems().size());
        verify(skuManager, never()).getMinPriceMapByProductIds(any());
    }

    @Test
    @DisplayName("图与向量皆无命中 — 空态 NONE（不报错）")
    void shouldReturnNone_whenBothPathsEmpty() {
        stubAnchor();
        when(kgClient.neighbors(any(), any(), any(), any(), anyInt(), any()))
                .thenReturn(List.of());
        when(productManager.similarProducts(ANCHOR_ID, 6)).thenReturn(List.of());

        PairingCardVO card = kgPairingManager.pairing(ANCHOR_ID, null);

        assertEquals(PairingCardVO.SOURCE_NONE, card.getSource());
        assertTrue(card.getItems().isEmpty());
    }

    // ==================== 锚点守卫与 fail-open ====================

    @Test
    @DisplayName("锚点商品不存在/不在售 — 空态 NONE，且不产生图查询")
    void shouldReturnNoneWithoutGraphQuery_whenAnchorMissingOrOffShelf() {
        when(productRepository.findById(ANCHOR_ID)).thenReturn(null);
        assertEquals(PairingCardVO.SOURCE_NONE, kgPairingManager.pairing(ANCHOR_ID, null).getSource());

        when(productRepository.findById(ANCHOR_ID))
                .thenReturn(product(ANCHOR_ID, ANCHOR_CATEGORY, 50, ProductStatusEnum.OFF_SHELF));
        assertEquals(PairingCardVO.SOURCE_NONE, kgPairingManager.pairing(ANCHOR_ID, null).getSource());

        verify(kgClient, never()).neighbors(any(), any(), any(), any(), anyInt(), any());
    }

    @Test
    @DisplayName("全链路异常（Feign 意外抛错/仓储异常）— 空态 NONE 不报错（US-KG-9）")
    void shouldReturnNone_whenAnyExceptionThrown() {
        doReturn(product(ANCHOR_ID, ANCHOR_CATEGORY, 50, ProductStatusEnum.ON_SHELF))
                .when(productRepository).findById(ANCHOR_ID);
        doThrow(new RuntimeException("unexpected")).when(kgClient)
                .neighbors(any(), any(), any(), any(), anyInt(), any());
        assertEquals(PairingCardVO.SOURCE_NONE, kgPairingManager.pairing(ANCHOR_ID, null).getSource());

        doThrow(new RuntimeException("db down")).when(productRepository).findById(ANCHOR_ID);
        assertEquals(PairingCardVO.SOURCE_NONE, kgPairingManager.pairing(ANCHOR_ID, null).getSource());

        doReturn(product(ANCHOR_ID, ANCHOR_CATEGORY, 50, ProductStatusEnum.ON_SHELF))
                .when(productRepository).findById(ANCHOR_ID);
        doReturn(List.of()).when(kgClient).neighbors(any(), any(), any(), any(), anyInt(), any());
        when(productManager.similarProducts(ANCHOR_ID, 6)).thenThrow(new RuntimeException("boom"));
        assertEquals(PairingCardVO.SOURCE_NONE, kgPairingManager.pairing(ANCHOR_ID, null).getSource());
    }

    // ==================== limit 归一 ====================

    @Test
    @DisplayName("limit 归一 — 缺省 6 / 非正回落 6 / 上限 10（P35 契约口径）")
    void shouldNormalizeLimit() {
        stubAnchor();
        when(kgClient.neighbors(any(), any(), any(), any(), anyInt(), any())).thenReturn(List.of());
        when(productManager.similarProducts(anyLong(), anyInt())).thenReturn(List.of());

        kgPairingManager.pairing(ANCHOR_ID, null);
        kgPairingManager.pairing(ANCHOR_ID, 0);
        kgPairingManager.pairing(ANCHOR_ID, 50);

        verify(productManager, times(2)).similarProducts(ANCHOR_ID, 6);
        verify(productManager, times(1)).similarProducts(ANCHOR_ID, 10);
    }
}
