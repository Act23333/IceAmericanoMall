package org.icedamericanomall.marketing.app.promotion;

import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.marketing.adapter.assembler.MarketingConverter;
import org.icedamericanomall.marketing.adapter.response.ActivityVO;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * ActivityManager 活动中心用例单元测试（V5.11 M12/M13）。
 *
 * <p>覆盖：缺省全量列表、按契约类型过滤、NEW_USER 空列表（Phase 2 无数据源）、
 * 无活动空集合、详情字段装配（type 数字编码 → 契约字符串）、详情不存在 → 1103、
 * 非法类型 → 1101。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ActivityManager — M12/M13 活动中心单元测试")
class ActivityManagerTest {

    private static final Long SELLER_ID = 5L;

    @Mock
    private PromotionRepository promotionRepository;

    private final MarketingConverter converter = Mappers.getMapper(MarketingConverter.class);

    private ActivityManager activityManager;

    @BeforeEach
    void setUp() {
        activityManager = new ActivityManager(promotionRepository, converter);
    }

    @Test
    @DisplayName("list — type 缺省 → 全店铺进行中活动全量返回（type 装配为契约字符串）")
    void shouldReturnAllActive_whenTypeAbsent() {
        when(promotionRepository.findAllActive(any(LocalDateTime.class)))
                .thenReturn(List.of(promotion(1L, 1), promotion(2L, 2)));

        List<ActivityVO> result = activityManager.listActiveActivities(null);

        assertEquals(2, result.size());
        assertEquals("FULL_REDUCE", result.get(0).getType());
        assertEquals("LIMIT_DISCOUNT", result.get(1).getType());

        ArgumentCaptor<LocalDateTime> nowCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(promotionRepository).findAllActive(nowCaptor.capture());
        assertTrue(nowCaptor.getValue().isBefore(LocalDateTime.now().plusMinutes(1)),
                "应以当前时刻判定进行中");
    }

    @Test
    @DisplayName("list — type=FULL_REDUCE → 仅保留满减活动（type=1）")
    void shouldFilterFullReduce_whenTypeGiven() {
        when(promotionRepository.findAllActive(any(LocalDateTime.class)))
                .thenReturn(List.of(promotion(1L, 1), promotion(2L, 2), promotion(3L, 1)));

        List<ActivityVO> result = activityManager.listActiveActivities("FULL_REDUCE");

        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(vo -> "FULL_REDUCE".equals(vo.getType())));
        assertTrue(result.stream().allMatch(vo -> vo.getId() == 1L || vo.getId() == 3L));
    }

    @Test
    @DisplayName("list — type=NEW_USER → 空列表（promotion 暂无数据源，Phase 2），不触达仓储")
    void shouldReturnEmptyList_whenNewUserType() {
        List<ActivityVO> result = activityManager.listActiveActivities("NEW_USER");

        assertTrue(result.isEmpty());
        verifyNoInteractions(promotionRepository);
    }

    @Test
    @DisplayName("list — 无进行中活动 → 空集合（合法业务态，非查询失败）")
    void shouldReturnEmptyList_whenNoActivePromotion() {
        when(promotionRepository.findAllActive(any(LocalDateTime.class))).thenReturn(List.of());

        List<ActivityVO> result = activityManager.listActiveActivities("");

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("list — type 非契约三值之一 → PARAM_ERROR(1101)，不触达仓储")
    void shouldThrowParamError_whenTypeUnknown() {
        BizException ex = assertThrows(BizException.class,
                () -> activityManager.listActiveActivities("GROUP_BUY"));

        assertEquals(ErrorCode.PARAM_ERROR, ex.getErrorCode());
        verify(promotionRepository, never()).findAllActive(any(LocalDateTime.class));
    }

    @Test
    @DisplayName("detail — 活动存在 → 返回 VO，字段与契约 JSON 逐字一致（type=契约字符串）")
    void shouldReturnActivity_whenDetailExists() {
        Promotion promotion = promotion(1L, 1);
        when(promotionRepository.findById(1L)).thenReturn(promotion);

        ActivityVO vo = activityManager.getActivityDetail(1L);

        assertEquals(1L, vo.getId());
        assertEquals("FULL_REDUCE", vo.getType());
        assertEquals("春季家装满减", vo.getName());
        assertEquals(SELLER_ID, vo.getSellerId());
        assertEquals(30000, vo.getThreshold());
        assertEquals(4000, vo.getDiscountValue());
        assertEquals(0, vo.getMaxDiscount());
        assertEquals(promotion.getStartTime(), vo.getStartTime());
        assertEquals(promotion.getEndTime(), vo.getEndTime());
    }

    @Test
    @DisplayName("detail — 活动不存在 → NO_RESOURCE_FOUND(1103)")
    void shouldThrowNoResourceFound_whenDetailMissing() {
        when(promotionRepository.findById(99L)).thenReturn(null);

        BizException ex = assertThrows(BizException.class,
                () -> activityManager.getActivityDetail(99L));

        assertEquals(ErrorCode.NO_RESOURCE_FOUND, ex.getErrorCode());
    }

    /** 构造一个满减口径的促销聚合（时间窗覆盖当前时刻，即进行中）。 */
    private Promotion promotion(Long id, int type) {
        return Promotion.builder()
                .id(id)
                .name("春季家装满减")
                .sellerId(SELLER_ID)
                .type(type)
                .threshold(30000)
                .discountValue(4000)
                .maxDiscount(0)
                .startTime(LocalDateTime.now().minusDays(1))
                .endTime(LocalDateTime.now().plusDays(30))
                .build();
    }
}
