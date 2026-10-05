package org.icedamericanomall.marketing.app.promotion;

import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.marketing.adapter.assembler.MarketingConverter;
import org.icedamericanomall.marketing.adapter.request.PromotionCreateReq;
import org.icedamericanomall.marketing.adapter.request.PromotionUpdateReq;
import org.icedamericanomall.marketing.adapter.response.PromotionVO;
import org.icedamericanomall.marketing.domain.enums.PromotionStatusEnum;
import org.icedamericanomall.marketing.domain.model.promotion.Promotion;
import org.icedamericanomall.marketing.domain.repository.PromotionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PromotionAdminManager 管理端用例单元测试（V5.8 AD48-AD50）。
 *
 * <p>覆盖：创建三态时间窗推导（未来=未开始 / 当前=进行中 / 已过期拒绝——已结束不可能由创建产生）、
 * 时间窗无序 / 满折越界 / 类型非法 → 1101、maxDiscount 缺省归一 0、
 * null-safe 局部更新 + 状态重推导、已结束禁编、进行中禁删、不存在 → 1103。
 * 装配器用真实 MapStruct 实现（验证 Req→聚合 与 null-safe 合并的真实行为）。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PromotionAdminManager — AD48-AD50 创建/编辑/删除单元测试")
class PromotionAdminManagerTest {

    private static final Long PROMOTION_ID = 1L;
    private static final Long GENERATED_ID = 101L;
    private static final Long SELLER_ID = 500L;
    private static final int TYPE_FIXED = 1;
    private static final int TYPE_PERCENTAGE = 2;

    @Mock
    private PromotionRepository promotionRepository;

    private final MarketingConverter converter = Mappers.getMapper(MarketingConverter.class);

    private PromotionAdminManager promotionAdminManager;

    @BeforeEach
    void setUp() {
        promotionAdminManager = new PromotionAdminManager(promotionRepository, converter);
        // 仓储 INSERT 主键回填（模拟 MyBatis-Plus 生成主键后写回聚合）
        lenient().doAnswer(invocation -> {
            Promotion promotion = invocation.getArgument(0);
            promotion.setId(GENERATED_ID);
            return null;
        }).when(promotionRepository).save(any(Promotion.class));
    }

    // ==================== AD48 创建 ====================

    @Test
    @DisplayName("create — 未来时间窗 → 落库状态=未开始(1)，主键回填进 VO，maxDiscount 缺省归一 0")
    void shouldDerivePendingStatus_whenCreateWithFutureWindow() {
        LocalDateTime now = LocalDateTime.now();

        PromotionVO vo = promotionAdminManager.createPromotion(
                createReq(TYPE_FIXED, now.plusHours(1), now.plusHours(3)));

        ArgumentCaptor<Promotion> captor = ArgumentCaptor.forClass(Promotion.class);
        verify(promotionRepository).save(captor.capture());
        Promotion saved = captor.getValue();
        assertEquals(PromotionStatusEnum.PENDING.getCode(), saved.getStatus());
        assertEquals(0, saved.getMaxDiscount());
        assertEquals(GENERATED_ID, vo.getId());
        assertEquals(PromotionStatusEnum.PENDING.getCode(), vo.getStatus());
    }

    @Test
    @DisplayName("create — 当前进行中的时间窗 → 落库状态=进行中(2)")
    void shouldDeriveActiveStatus_whenCreateWithCurrentWindow() {
        LocalDateTime now = LocalDateTime.now();

        promotionAdminManager.createPromotion(
                createReq(TYPE_FIXED, now.minusMinutes(30), now.plusHours(2)));

        ArgumentCaptor<Promotion> captor = ArgumentCaptor.forClass(Promotion.class);
        verify(promotionRepository).save(captor.capture());
        assertEquals(PromotionStatusEnum.ACTIVE.getCode(), captor.getValue().getStatus());
    }

    @Test
    @DisplayName("create — 时间窗已整体过期 → PARAM_ERROR 拒绝，已结束状态不可能由创建产生")
    void shouldRejectCreate_whenTimeWindowAlreadyOver() {
        LocalDateTime now = LocalDateTime.now();

        BizException ex = assertThrows(BizException.class,
                () -> promotionAdminManager.createPromotion(
                        createReq(TYPE_FIXED, now.minusHours(2), now.minusHours(1))));

        assertEquals(ErrorCode.PARAM_ERROR, ex.getErrorCode());
        verify(promotionRepository, never()).save(any());
    }

    @Test
    @DisplayName("create — endTime 不晚于 startTime → PARAM_ERROR 拒绝")
    void shouldRejectCreate_whenEndTimeNotAfterStartTime() {
        LocalDateTime now = LocalDateTime.now();

        BizException ex = assertThrows(BizException.class,
                () -> promotionAdminManager.createPromotion(
                        createReq(TYPE_FIXED, now.plusHours(1), now.plusHours(1))));

        assertEquals(ErrorCode.PARAM_ERROR, ex.getErrorCode());
        verify(promotionRepository, never()).save(any());
    }

    @Test
    @DisplayName("create — 满折 discountValue=10000（契约边界）→ 创建成功")
    void shouldCreate_whenPercentageDiscountAtBoundary() {
        LocalDateTime now = LocalDateTime.now();
        PromotionCreateReq req = createReq(TYPE_PERCENTAGE, now.plusHours(1), now.plusHours(3));
        req.setDiscountValue(10000);

        promotionAdminManager.createPromotion(req);

        verify(promotionRepository).save(any(Promotion.class));
    }

    @ParameterizedTest
    @ValueSource(ints = {10001, 20000})
    @DisplayName("create — 满折 discountValue 越界（>10000 万分比）→ PARAM_ERROR 拒绝")
    void shouldRejectCreate_whenPercentageDiscountOutOfRange(int discountValue) {
        LocalDateTime now = LocalDateTime.now();
        PromotionCreateReq req = createReq(TYPE_PERCENTAGE, now.plusHours(1), now.plusHours(3));
        req.setDiscountValue(discountValue);

        BizException ex = assertThrows(BizException.class,
                () -> promotionAdminManager.createPromotion(req));

        assertEquals(ErrorCode.PARAM_ERROR, ex.getErrorCode());
        verify(promotionRepository, never()).save(any());
    }

    @Test
    @DisplayName("create — 满减 discountValue 不受万分比上限约束（金额口径）→ 创建成功")
    void shouldCreate_whenFixedDiscountAbovePercentageCap() {
        LocalDateTime now = LocalDateTime.now();
        PromotionCreateReq req = createReq(TYPE_FIXED, now.plusHours(1), now.plusHours(3));
        req.setDiscountValue(50000);

        promotionAdminManager.createPromotion(req);

        verify(promotionRepository).save(any(Promotion.class));
    }

    @Test
    @DisplayName("create — 类型非法（type=3）→ PARAM_ERROR 拒绝")
    void shouldRejectCreate_whenTypeIllegal() {
        LocalDateTime now = LocalDateTime.now();

        BizException ex = assertThrows(BizException.class,
                () -> promotionAdminManager.createPromotion(
                        createReq(3, now.plusHours(1), now.plusHours(3))));

        assertEquals(ErrorCode.PARAM_ERROR, ex.getErrorCode());
        verify(promotionRepository, never()).save(any());
    }

    // ==================== AD49 编辑 ====================

    @Test
    @DisplayName("update — 仅传 name → null-safe 只改名称，其余字段不动")
    void shouldMergeOnlyProvidedFields_whenUpdatePartialBody() {
        LocalDateTime start = LocalDateTime.now().minusHours(1);
        LocalDateTime end = LocalDateTime.now().plusHours(3);
        Promotion existing = promotion(start, end, TYPE_FIXED);
        when(promotionRepository.findById(PROMOTION_ID)).thenReturn(existing);

        PromotionUpdateReq req = new PromotionUpdateReq();
        req.setName("双11满减");

        promotionAdminManager.updatePromotion(PROMOTION_ID, req);

        assertEquals("双11满减", existing.getName());
        assertEquals(SELLER_ID, existing.getSellerId());
        assertEquals(10000, existing.getThreshold());
        assertEquals(20000, existing.getMaxDiscount());
        assertEquals(start, existing.getStartTime());
        assertEquals(end, existing.getEndTime());
        assertEquals(PromotionStatusEnum.ACTIVE.getCode(), existing.getStatus());
        verify(promotionRepository).update(existing);
        verify(promotionRepository, never()).save(any());
    }

    @Test
    @DisplayName("update — 时间窗整体后移 → 状态重推导为未开始(1)落库")
    void shouldRederiveStatus_whenUpdateShiftsWindow() {
        Promotion existing = promotion(LocalDateTime.now().minusHours(1),
                LocalDateTime.now().plusHours(3), TYPE_FIXED);
        when(promotionRepository.findById(PROMOTION_ID)).thenReturn(existing);
        LocalDateTime now = LocalDateTime.now();

        PromotionUpdateReq req = new PromotionUpdateReq();
        req.setStartTime(now.plusHours(2));
        req.setEndTime(now.plusHours(5));

        promotionAdminManager.updatePromotion(PROMOTION_ID, req);

        assertEquals(PromotionStatusEnum.PENDING.getCode(), existing.getStatus());
        verify(promotionRepository).update(existing);
    }

    @Test
    @DisplayName("update — 传入满折 discountValue 越界 → PARAM_ERROR 拒绝，不落库")
    void shouldRejectUpdate_whenMergedPercentageDiscountOutOfRange() {
        Promotion existing = promotion(LocalDateTime.now().plusHours(1),
                LocalDateTime.now().plusHours(3), TYPE_PERCENTAGE);
        when(promotionRepository.findById(PROMOTION_ID)).thenReturn(existing);

        PromotionUpdateReq req = new PromotionUpdateReq();
        req.setDiscountValue(12000);

        BizException ex = assertThrows(BizException.class,
                () -> promotionAdminManager.updatePromotion(PROMOTION_ID, req));

        assertEquals(ErrorCode.PARAM_ERROR, ex.getErrorCode());
        verify(promotionRepository, never()).update(any());
    }

    @Test
    @DisplayName("update — 传满折折扣但保持满减类型 → 聚合校验放行（满减为金额口径）")
    void shouldPassUpdate_whenFixedTypeKeptWithLargeDiscount() {
        Promotion existing = promotion(LocalDateTime.now().plusHours(1),
                LocalDateTime.now().plusHours(3), TYPE_FIXED);
        when(promotionRepository.findById(PROMOTION_ID)).thenReturn(existing);

        PromotionUpdateReq req = new PromotionUpdateReq();
        req.setDiscountValue(12000);

        promotionAdminManager.updatePromotion(PROMOTION_ID, req);

        assertEquals(12000, existing.getDiscountValue());
        verify(promotionRepository).update(existing);
    }

    @Test
    @DisplayName("update — 已结束活动 → PARAM_ERROR 拒绝编辑，不落库")
    void shouldRejectUpdate_whenActivityAlreadyEnded() {
        when(promotionRepository.findById(PROMOTION_ID)).thenReturn(promotion(
                LocalDateTime.now().minusHours(3), LocalDateTime.now().minusHours(1), TYPE_FIXED));

        BizException ex = assertThrows(BizException.class,
                () -> promotionAdminManager.updatePromotion(PROMOTION_ID, new PromotionUpdateReq()));

        assertEquals(ErrorCode.PARAM_ERROR, ex.getErrorCode());
        verify(promotionRepository, never()).update(any());
    }

    @Test
    @DisplayName("update — 活动不存在 → NO_RESOURCE_FOUND(1103)")
    void shouldThrowNoResourceFound_whenUpdateMissingActivity() {
        when(promotionRepository.findById(PROMOTION_ID)).thenReturn(null);

        BizException ex = assertThrows(BizException.class,
                () -> promotionAdminManager.updatePromotion(PROMOTION_ID, new PromotionUpdateReq()));

        assertEquals(ErrorCode.NO_RESOURCE_FOUND, ex.getErrorCode());
    }

    // ==================== AD50 删除 ====================

    @Test
    @DisplayName("delete — 进行中活动 → PARAM_ERROR 禁删，不物理删除")
    void shouldRejectDelete_whenActivityActive() {
        when(promotionRepository.findById(PROMOTION_ID)).thenReturn(promotion(
                LocalDateTime.now().minusHours(1), LocalDateTime.now().plusHours(3), TYPE_FIXED));

        BizException ex = assertThrows(BizException.class,
                () -> promotionAdminManager.deletePromotion(PROMOTION_ID));

        assertEquals(ErrorCode.PARAM_ERROR, ex.getErrorCode());
        verify(promotionRepository, never()).deleteById(any());
    }

    @Test
    @DisplayName("delete — 已结束活动 → 物理删除成功")
    void shouldDelete_whenActivityEnded() {
        when(promotionRepository.findById(PROMOTION_ID)).thenReturn(promotion(
                LocalDateTime.now().minusHours(3), LocalDateTime.now().minusHours(1), TYPE_FIXED));

        promotionAdminManager.deletePromotion(PROMOTION_ID);

        verify(promotionRepository).deleteById(PROMOTION_ID);
    }

    @Test
    @DisplayName("delete — 未开始活动 → 物理删除成功")
    void shouldDelete_whenActivityNotStarted() {
        when(promotionRepository.findById(PROMOTION_ID)).thenReturn(promotion(
                LocalDateTime.now().plusHours(1), LocalDateTime.now().plusHours(3), TYPE_FIXED));

        promotionAdminManager.deletePromotion(PROMOTION_ID);

        verify(promotionRepository).deleteById(PROMOTION_ID);
    }

    @Test
    @DisplayName("delete — 活动不存在 → NO_RESOURCE_FOUND(1103)")
    void shouldThrowNoResourceFound_whenDeleteMissingActivity() {
        when(promotionRepository.findById(PROMOTION_ID)).thenReturn(null);

        BizException ex = assertThrows(BizException.class,
                () -> promotionAdminManager.deletePromotion(PROMOTION_ID));

        assertEquals(ErrorCode.NO_RESOURCE_FOUND, ex.getErrorCode());
        verify(promotionRepository, never()).deleteById(any());
    }

    // ==================== 构造辅助 ====================

    private PromotionCreateReq createReq(int type, LocalDateTime start, LocalDateTime end) {
        PromotionCreateReq req = new PromotionCreateReq();
        req.setName("双11店铺满减");
        req.setSellerId(SELLER_ID);
        req.setType(type);
        req.setThreshold(10000);
        req.setDiscountValue(2000);
        req.setStartTime(start);
        req.setEndTime(end);
        return req;
    }

    private Promotion promotion(LocalDateTime start, LocalDateTime end, int type) {
        return Promotion.builder()
                .id(PROMOTION_ID)
                .name("双11店铺满减")
                .sellerId(SELLER_ID)
                .type(type)
                .threshold(10000)
                .discountValue(2000)
                .maxDiscount(20000)
                .status(PromotionStatusEnum.ACTIVE.getCode())
                .version(0)
                .startTime(start)
                .endTime(end)
                .build();
    }
}
