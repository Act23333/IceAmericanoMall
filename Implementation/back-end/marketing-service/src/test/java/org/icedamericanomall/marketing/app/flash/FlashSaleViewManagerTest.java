package org.icedamericanomall.marketing.app.flash;

import org.icedamericanomall.marketing.adapter.assembler.MarketingConverterImpl;
import org.icedamericanomall.marketing.adapter.response.FlashSaleVO;
import org.icedamericanomall.marketing.app.dto.FlashSaleSpuBrief;
import org.icedamericanomall.marketing.app.port.FlashSaleSpuBriefQuery;
import org.icedamericanomall.marketing.domain.model.flash.FlashSale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 秒杀视图装配单元测试（契约 §4.10.2 {@code FlashSaleVO} 字段投影）。
 *
 * <p>覆盖：①商品名/主图/原价被正确回填且整页只查一次（无 N+1）；
 * ②摘要缺失 / 数据源异常时字段置空但不抛异常（fail-open）；
 * ③{@code progress_percent} 由秒杀总库存推导（stock=0 安全）。
 */
@DisplayName("秒杀视图装配 — FlashSaleVO 字段投影（契约 §4.10.2）")
@ExtendWith(MockitoExtension.class)
class FlashSaleViewManagerTest {

    private static final Long FLASH_ID = 1L;
    private static final Long SKU_ID = 10L;
    private static final Long SPU_ID = 100L;

    @Mock
    private FlashSaleSpuBriefQuery flashSaleSpuBriefQuery;

    private FlashSaleViewManager flashSaleViewManager;

    @BeforeEach
    void setUp() {
        flashSaleViewManager = new FlashSaleViewManager(new MarketingConverterImpl(), flashSaleSpuBriefQuery);
    }

    @Test
    @DisplayName("toViewList — 商品摘要在 → product_name/product_image/original_price 被正确填充")
    void shouldFillSpuFields_whenBriefAvailable() {
        when(flashSaleSpuBriefQuery.findBySkuIds(List.of(SKU_ID)))
                .thenReturn(Map.of(SKU_ID, new FlashSaleSpuBrief(SKU_ID, "手工陶瓷茶杯",
                        "https://cdn.icedmall.com/product/main_100.jpg", 12800)));

        List<FlashSaleVO> views = flashSaleViewManager.toViewList(List.of(flashSale(100, 35)));

        FlashSaleVO vo = views.get(0);
        assertEquals(FLASH_ID, vo.getId());
        assertEquals(SKU_ID, vo.getSkuId());
        assertEquals(SPU_ID, vo.getSpuId());
        assertEquals("手工陶瓷茶杯", vo.getProductName());
        assertEquals("https://cdn.icedmall.com/product/main_100.jpg", vo.getProductImage());
        assertEquals(12800, vo.getOriginalPrice());
        assertEquals(9900, vo.getFlashPrice());
    }

    @Test
    @DisplayName("toViewList — 多场次只查一次商品摘要，SKU ID 去重")
    void shouldQueryBriefOnce_whenMultipleRows() {
        when(flashSaleSpuBriefQuery.findBySkuIds(any())).thenReturn(Map.of());
        FlashSale second = flashSale(50, 0);
        second.setId(2L);

        flashSaleViewManager.toViewList(List.of(flashSale(100, 0), second));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<Long>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(flashSaleSpuBriefQuery).findBySkuIds(captor.capture());
        assertEquals(List.of(SKU_ID), List.copyOf(captor.getValue()), "同一 SKU 只查一次");
    }

    @Test
    @DisplayName("toViewList — 商品摘要缺失（SKU 已删除）→ 三字段为 null，主流程不失败")
    void shouldLeaveSpuFieldsNull_whenBriefMissing() {
        when(flashSaleSpuBriefQuery.findBySkuIds(List.of(SKU_ID))).thenReturn(Map.of());

        List<FlashSaleVO> views = flashSaleViewManager.toViewList(List.of(flashSale(100, 35)));

        assertEquals(1, views.size());
        assertNull(views.get(0).getProductName());
        assertNull(views.get(0).getProductImage());
        assertNull(views.get(0).getOriginalPrice());
        assertEquals(9900, views.get(0).getFlashPrice(), "场次本体字段不受影响");
    }

    @Test
    @DisplayName("toViewList — 商品摘要数据源异常 → 降级为空字段（fail-open），列表照常返回")
    void shouldDegradeGracefully_whenBriefQueryThrows() {
        when(flashSaleSpuBriefQuery.findBySkuIds(List.of(SKU_ID)))
                .thenThrow(new IllegalStateException("item 域不可用"));

        List<FlashSaleVO> views = flashSaleViewManager.toViewList(List.of(flashSale(100, 35)));

        assertEquals(1, views.size());
        assertNull(views.get(0).getProductName());
    }

    @Test
    @DisplayName("toView — 进度百分比 = 已售 / 秒杀总库存（契约样例 35/100 → 35）")
    void shouldComputeProgressPercent_fromSoldAndTotalStock() {
        when(flashSaleSpuBriefQuery.findBySkuIds(any())).thenReturn(Map.of());

        assertEquals(35, flashSaleViewManager.toView(flashSale(100, 35)).getProgressPercent());
        assertEquals(100, flashSaleViewManager.toView(flashSale(100, 150)).getProgressPercent(),
                "超卖等异常数据夹取到 100");
        assertEquals(0, flashSaleViewManager.toView(flashSale(0, 5)).getProgressPercent(),
                "总库存缺失/为 0 时返回 0，不抛异常");
    }

    @Test
    @DisplayName("toViewList — 空列表不触发商品摘要查询")
    void shouldSkipBriefQuery_whenListEmpty() {
        assertTrue(flashSaleViewManager.toViewList(List.of()).isEmpty());
        assertTrue(flashSaleViewManager.toViewList(null).isEmpty());
        verify(flashSaleSpuBriefQuery, never()).findBySkuIds(any());
    }

    private FlashSale flashSale(int stock, int soldCount) {
        return FlashSale.builder()
                .id(FLASH_ID)
                .spuId(SPU_ID)
                .skuId(SKU_ID)
                .flashPrice(9900)
                .stock(stock)
                .soldCount(soldCount)
                .startTime(LocalDateTime.of(2026, 8, 2, 10, 0))
                .endTime(LocalDateTime.of(2026, 8, 2, 12, 0))
                .status(2)
                .build();
    }
}
