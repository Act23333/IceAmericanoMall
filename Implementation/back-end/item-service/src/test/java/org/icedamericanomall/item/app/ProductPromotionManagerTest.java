package org.icedamericanomall.item.app;

import org.icedamericanomall.client.PromotionClient;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.dto.PromotionCO;
import org.icedamericanomall.dto.PromotionQueryCO;
import org.icedamericanomall.item.adapter.response.PromotionTagVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * ProductPromotionManager 单元测试 —— 促销标签映射（type 1/2 → 契约字符串）、
 * 无活动/降级空列表（fail-open）、Caffeine 按 sellerId 短 TTL 缓存。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ProductPromotionManager 促销标签编排单元测试")
class ProductPromotionManagerTest {

    @Mock
    private PromotionClient promotionClient;

    private ProductPromotionManager productPromotionManager;

    @BeforeEach
    void setUp() {
        productPromotionManager = new ProductPromotionManager(promotionClient);
    }

    /** I44 促销（PromotionCO 全参构造：id/name/sellerId/type/threshold/discountValue/maxDiscount）。 */
    private PromotionCO promotion(Integer type) {
        return new PromotionCO(1L, "店铺大促", 100L, type, 10000, 2000, 0, null);
    }

    // ==================== 标签映射 ====================

    @Test
    @DisplayName("tagsFor — type=1 满减：映射 FULL_REDUCE，字段逐字透传，endTime 预留 null")
    void shouldMapFullReduceTag_whenTypeIsOne() {
        when(promotionClient.queryActive(any(PromotionQueryCO.class)))
                .thenReturn(List.of(promotion(1)));

        List<PromotionTagVO> tags = productPromotionManager.tagsFor(100L);

        assertEquals(1, tags.size());
        PromotionTagVO tag = tags.get(0);
        assertEquals("FULL_REDUCE", tag.getType());
        assertEquals("店铺大促", tag.getName());
        assertEquals(10000, tag.getThreshold());
        assertEquals(2000, tag.getDiscountValue());
        assertEquals(0, tag.getMaxDiscount());
        assertNull(tag.getEndTime());
    }

    @Test
    @DisplayName("tagsFor — type=2 满折：映射 LIMIT_DISCOUNT")
    void shouldMapLimitDiscountTag_whenTypeIsTwo() {
        when(promotionClient.queryActive(any(PromotionQueryCO.class)))
                .thenReturn(List.of(promotion(2)));

        List<PromotionTagVO> tags = productPromotionManager.tagsFor(100L);

        assertEquals(1, tags.size());
        assertEquals("LIMIT_DISCOUNT", tags.get(0).getType());
    }

    @Test
    @DisplayName("tagsFor — 契约外类型（防御性过滤）→ 不出标签")
    void shouldFilterUnknownType_whenMapping() {
        when(promotionClient.queryActive(any(PromotionQueryCO.class)))
                .thenReturn(List.of(promotion(9)));

        List<PromotionTagVO> tags = productPromotionManager.tagsFor(100L);

        assertTrue(tags.isEmpty());
    }

    // ==================== fail-open 降级 ====================

    @Test
    @DisplayName("tagsFor — 无活动：空列表不报错")
    void shouldReturnEmpty_whenNoActivePromotion() {
        when(promotionClient.queryActive(any(PromotionQueryCO.class))).thenReturn(List.of());

        List<PromotionTagVO> tags = productPromotionManager.tagsFor(100L);

        assertTrue(tags.isEmpty());
    }

    @Test
    @DisplayName("tagsFor — client fail-closed 抛 BizException（fallback 语义不变）→ 展示侧兜底空列表不抛")
    void shouldReturnEmpty_whenClientThrowsBizException() {
        when(promotionClient.queryActive(any(PromotionQueryCO.class)))
                .thenThrow(new BizException(ErrorCode.SYSTEM_BUSY, "促销服务暂不可用"));

        List<PromotionTagVO> tags = productPromotionManager.tagsFor(100L);

        assertTrue(tags.isEmpty());
    }

    @Test
    @DisplayName("tagsFor — 任意未预期异常同样兜底：空列表不抛，详情页不因促销失败而失败")
    void shouldReturnEmpty_whenClientThrowsUnexpected() {
        when(promotionClient.queryActive(any(PromotionQueryCO.class)))
                .thenThrow(new RuntimeException("connection reset"));

        List<PromotionTagVO> tags = productPromotionManager.tagsFor(100L);

        assertTrue(tags.isEmpty());
    }

    // ==================== 缓存 ====================

    @Test
    @DisplayName("tagsFor — 同 sellerId 二次查询命中缓存：仅一次 client 调用")
    void shouldHitCache_whenSameSellerQueriedTwice() {
        when(promotionClient.queryActive(any(PromotionQueryCO.class)))
                .thenReturn(List.of(promotion(1)));

        List<PromotionTagVO> first = productPromotionManager.tagsFor(100L);
        List<PromotionTagVO> second = productPromotionManager.tagsFor(100L);

        assertEquals(1, first.size());
        assertEquals(1, second.size());
        verify(promotionClient, times(1)).queryActive(any(PromotionQueryCO.class));
    }

    @Test
    @DisplayName("tagsFor — 不同 seller 各自加载：按 sellerId 分别发起 I44 查询")
    void shouldLoadSeparately_whenDifferentSellers() {
        when(promotionClient.queryActive(any(PromotionQueryCO.class))).thenReturn(List.of());

        productPromotionManager.tagsFor(100L);
        productPromotionManager.tagsFor(200L);

        ArgumentCaptor<PromotionQueryCO> captor = ArgumentCaptor.forClass(PromotionQueryCO.class);
        verify(promotionClient, times(2)).queryActive(captor.capture());
        assertEquals(100L, captor.getAllValues().get(0).getSellerId());
        assertEquals(200L, captor.getAllValues().get(1).getSellerId());
    }

    @Test
    @DisplayName("tagsFor — sellerId 为 null（商品无归属场景）：空列表且不触发 client")
    void shouldReturnEmptyWithoutClientCall_whenSellerIdNull() {
        List<PromotionTagVO> tags = productPromotionManager.tagsFor(null);

        assertTrue(tags.isEmpty());
        verifyNoInteractions(promotionClient);
    }
}
