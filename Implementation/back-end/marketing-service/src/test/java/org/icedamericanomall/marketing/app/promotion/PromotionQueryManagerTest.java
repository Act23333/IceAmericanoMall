package org.icedamericanomall.marketing.app.promotion;

import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.dto.PromotionCO;
import org.icedamericanomall.dto.PromotionQueryCO;
import org.icedamericanomall.marketing.adapter.assembler.MarketingConverter;
import org.icedamericanomall.marketing.domain.model.promotion.Promotion;
import org.icedamericanomall.marketing.domain.repository.PromotionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * PromotionQueryManager 查询用例单元测试（V5.8 I44）。
 *
 * <p>覆盖：sellerId 缺失 → 1101、按 sellerId + 时间窗委托仓储、聚合 → CO 装配字段、
 * 空集合 = 无生效活动（合法业务态）。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PromotionQueryManager — I44 生效促销查询单元测试")
class PromotionQueryManagerTest {

    private static final Long SELLER_ID = 500L;

    @Mock
    private PromotionRepository promotionRepository;

    private final MarketingConverter converter = Mappers.getMapper(MarketingConverter.class);

    private PromotionQueryManager promotionQueryManager;

    @BeforeEach
    void setUp() {
        promotionQueryManager = new PromotionQueryManager(promotionRepository, converter);
    }

    @Test
    @DisplayName("query — sellerId 缺失 → PARAM_ERROR，不触达仓储")
    void shouldThrowParamError_whenSellerIdMissing() {
        PromotionQueryCO query = new PromotionQueryCO();
        query.setSellerId(null);

        BizException ex = assertThrows(BizException.class,
                () -> promotionQueryManager.queryActive(query));

        assertEquals(ErrorCode.PARAM_ERROR, ex.getErrorCode());
        verifyNoInteractions(promotionRepository);
    }

    @Test
    @DisplayName("query — 按 sellerId + 当前时刻委托仓储，聚合装配为 CO 返回")
    void shouldDelegateAndConvert_whenSellerIdPresent() {
        LocalDateTime start = LocalDateTime.now().minusHours(1);
        LocalDateTime end = LocalDateTime.now().plusHours(3);
        Promotion promotion = Promotion.builder()
                .id(1L)
                .name("双11店铺满减")
                .sellerId(SELLER_ID)
                .type(2)
                .threshold(10000)
                .discountValue(8500)
                .maxDiscount(5000)
                .startTime(start)
                .endTime(end)
                .build();
        when(promotionRepository.findActiveBySeller(eq(SELLER_ID), any(LocalDateTime.class)))
                .thenReturn(List.of(promotion));

        List<PromotionCO> result = promotionQueryManager.queryActive(query(SELLER_ID));

        ArgumentCaptor<LocalDateTime> nowCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(promotionRepository).findActiveBySeller(eq(SELLER_ID), nowCaptor.capture());
        assertTrue(nowCaptor.getValue().isAfter(start.minusMinutes(1)), "应以当前时刻判定进行中");
        assertEquals(1, result.size());
        PromotionCO co = result.get(0);
        assertEquals(1L, co.getId());
        assertEquals("双11店铺满减", co.getName());
        assertEquals(SELLER_ID, co.getSellerId());
        assertEquals(2, co.getType());
        assertEquals(10000, co.getThreshold());
        assertEquals(8500, co.getDiscountValue());
        assertEquals(5000, co.getMaxDiscount());
    }

    @Test
    @DisplayName("query — 无生效活动 → 空集合（合法业务态，非查询失败）")
    void shouldReturnEmptyList_whenNoActivePromotion() {
        when(promotionRepository.findActiveBySeller(eq(SELLER_ID), any(LocalDateTime.class)))
                .thenReturn(List.of());

        List<PromotionCO> result = promotionQueryManager.queryActive(query(SELLER_ID));

        assertTrue(result.isEmpty());
    }

    private PromotionQueryCO query(Long sellerId) {
        PromotionQueryCO query = new PromotionQueryCO();
        query.setSellerId(sellerId);
        return query;
    }
}
