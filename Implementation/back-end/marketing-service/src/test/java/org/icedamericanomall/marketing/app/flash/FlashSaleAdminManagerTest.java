package org.icedamericanomall.marketing.app.flash;

import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.marketing.adapter.assembler.MarketingConverter;
import org.icedamericanomall.marketing.app.port.FlashSaleProductBriefQuery;
import org.icedamericanomall.marketing.adapter.request.FlashSaleCreateReq;
import org.icedamericanomall.marketing.adapter.request.FlashSaleUpdateReq;
import org.icedamericanomall.marketing.adapter.response.FlashSaleVO;
import org.icedamericanomall.marketing.domain.enums.FlashSaleStatusEnum;
import org.icedamericanomall.marketing.domain.model.flash.FlashSale;
import org.icedamericanomall.marketing.domain.repository.FlashSaleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * FlashSaleAdminManager 管理端用例单元测试（V5.7 AD40-AD42）。
 *
 * <p>覆盖：创建时间窗状态推导（未来=未开始 / 当前=进行中 / 已过期拒绝——已结束不可能由创建产生）、
 * null-safe 局部更新 + 状态重推导、已结束禁编守卫、进行中禁删守卫、
 * 不存在 → 1103。装配器用真实 MapStruct 实现（验证 Req→聚合 与 null-safe 合并的真实行为）。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("FlashSaleAdminManager — AD40-AD42 创建/编辑/删除单元测试")
class FlashSaleAdminManagerTest {

    private static final Long FLASH_ID = 1L;
    private static final Long GENERATED_ID = 101L;

    @Mock
    private FlashSaleRepository flashSaleRepository;

    @Mock
    private FlashSaleProductBriefQuery flashSaleProductBriefQuery;

    private final MarketingConverter converter = Mappers.getMapper(MarketingConverter.class);

    private FlashSaleAdminManager flashSaleAdminManager;

    @BeforeEach
    void setUp() {
        flashSaleAdminManager = new FlashSaleAdminManager(flashSaleRepository, converter,
                new FlashSaleViewManager(converter, flashSaleProductBriefQuery));
        // 仓储 INSERT 主键回填（模拟 MyBatis-Plus 生成主键后写回聚合）
        lenient().doAnswer(invocation -> {
            FlashSale sale = invocation.getArgument(0);
            sale.setId(GENERATED_ID);
            return null;
        }).when(flashSaleRepository).save(any(FlashSale.class));
    }

    // ==================== AD40 创建 ====================

    @Test
    @DisplayName("create — 未来时间窗 → 落库状态=未开始(1)，主键回填进 VO")
    void shouldDerivePendingStatus_whenCreateWithFutureWindow() {
        LocalDateTime now = LocalDateTime.now();

        FlashSaleVO vo = flashSaleAdminManager.createFlashSale(
                createReq(now.plusHours(1), now.plusHours(3)));

        ArgumentCaptor<FlashSale> captor = ArgumentCaptor.forClass(FlashSale.class);
        verify(flashSaleRepository).save(captor.capture());
        assertEquals(FlashSaleStatusEnum.PENDING.getCode(), captor.getValue().getStatus());
        assertEquals(0, captor.getValue().getSoldCount());
        assertEquals(GENERATED_ID, vo.getId());
        assertEquals(FlashSaleStatusEnum.PENDING.getCode(), vo.getStatus());
    }

    @Test
    @DisplayName("create — 当前进行中的时间窗 → 落库状态=进行中(2)")
    void shouldDeriveActiveStatus_whenCreateWithCurrentWindow() {
        LocalDateTime now = LocalDateTime.now();

        flashSaleAdminManager.createFlashSale(
                createReq(now.minusMinutes(30), now.plusHours(2)));

        ArgumentCaptor<FlashSale> captor = ArgumentCaptor.forClass(FlashSale.class);
        verify(flashSaleRepository).save(captor.capture());
        assertEquals(FlashSaleStatusEnum.ACTIVE.getCode(), captor.getValue().getStatus());
    }

    @Test
    @DisplayName("create — 时间窗已整体过期 → PARAM_ERROR 拒绝，已结束状态不可能由创建产生")
    void shouldRejectCreate_whenTimeWindowAlreadyOver() {
        LocalDateTime now = LocalDateTime.now();

        BizException ex = assertThrows(BizException.class,
                () -> flashSaleAdminManager.createFlashSale(
                        createReq(now.minusHours(2), now.minusHours(1))));

        assertEquals(ErrorCode.PARAM_ERROR, ex.getErrorCode());
        verify(flashSaleRepository, never()).save(any());
    }

    @Test
    @DisplayName("create — endTime 不晚于 startTime → PARAM_ERROR 拒绝")
    void shouldRejectCreate_whenEndTimeNotAfterStartTime() {
        LocalDateTime now = LocalDateTime.now();

        BizException ex = assertThrows(BizException.class,
                () -> flashSaleAdminManager.createFlashSale(
                        createReq(now.plusHours(1), now.plusHours(1))));

        assertEquals(ErrorCode.PARAM_ERROR, ex.getErrorCode());
        verify(flashSaleRepository, never()).save(any());
    }

    // ==================== AD41 编辑 ====================

    @Test
    @DisplayName("update — 仅传 flashPrice → null-safe 只改价格，库存/时间窗/已售不动")
    void shouldMergeOnlyProvidedFields_whenUpdatePartialBody() {
        LocalDateTime start = LocalDateTime.now().minusHours(1);
        LocalDateTime end = LocalDateTime.now().plusHours(3);
        FlashSale existing = sale(start, end, 100, 20);
        when(flashSaleRepository.findById(FLASH_ID)).thenReturn(existing);

        FlashSaleUpdateReq req = new FlashSaleUpdateReq();
        req.setFlashPrice(5900);

        flashSaleAdminManager.updateFlashSale(FLASH_ID, req);

        assertEquals(5900, existing.getFlashPrice());
        assertEquals(100, existing.getStock());
        assertEquals(20, existing.getSoldCount());
        assertEquals(start, existing.getStartTime());
        assertEquals(end, existing.getEndTime());
        assertEquals(FlashSaleStatusEnum.ACTIVE.getCode(), existing.getStatus());
        verify(flashSaleRepository).update(existing);
        verify(flashSaleRepository, never()).save(any());
    }

    @Test
    @DisplayName("update — 时间窗整体后移 → 状态重推导为未开始(1)落库")
    void shouldRederiveStatus_whenUpdateShiftsWindow() {
        FlashSale existing = sale(LocalDateTime.now().minusHours(1),
                LocalDateTime.now().plusHours(3), 100, 20);
        when(flashSaleRepository.findById(FLASH_ID)).thenReturn(existing);
        LocalDateTime now = LocalDateTime.now();

        FlashSaleUpdateReq req = new FlashSaleUpdateReq();
        req.setStartTime(now.plusHours(2));
        req.setEndTime(now.plusHours(5));

        flashSaleAdminManager.updateFlashSale(FLASH_ID, req);

        assertEquals(FlashSaleStatusEnum.PENDING.getCode(), existing.getStatus());
        verify(flashSaleRepository).update(existing);
    }

    @Test
    @DisplayName("update — 已结束活动 → PARAM_ERROR 拒绝编辑，不落库")
    void shouldRejectUpdate_whenActivityAlreadyEnded() {
        when(flashSaleRepository.findById(FLASH_ID)).thenReturn(sale(
                LocalDateTime.now().minusHours(3), LocalDateTime.now().minusHours(1), 100, 20));

        BizException ex = assertThrows(BizException.class,
                () -> flashSaleAdminManager.updateFlashSale(FLASH_ID, new FlashSaleUpdateReq()));

        assertEquals(ErrorCode.PARAM_ERROR, ex.getErrorCode());
        verify(flashSaleRepository, never()).update(any());
    }

    @Test
    @DisplayName("update — 活动不存在 → NO_RESOURCE_FOUND(1103)")
    void shouldThrowNoResourceFound_whenUpdateMissingActivity() {
        when(flashSaleRepository.findById(FLASH_ID)).thenReturn(null);

        BizException ex = assertThrows(BizException.class,
                () -> flashSaleAdminManager.updateFlashSale(FLASH_ID, new FlashSaleUpdateReq()));

        assertEquals(ErrorCode.NO_RESOURCE_FOUND, ex.getErrorCode());
    }

    // ==================== AD42 删除 ====================

    @Test
    @DisplayName("delete — 进行中活动 → PARAM_ERROR 禁删，不物理删除")
    void shouldRejectDelete_whenActivityActive() {
        when(flashSaleRepository.findById(FLASH_ID)).thenReturn(sale(
                LocalDateTime.now().minusHours(1), LocalDateTime.now().plusHours(3), 100, 20));

        BizException ex = assertThrows(BizException.class,
                () -> flashSaleAdminManager.deleteFlashSale(FLASH_ID));

        assertEquals(ErrorCode.PARAM_ERROR, ex.getErrorCode());
        verify(flashSaleRepository, never()).deleteById(any());
    }

    @Test
    @DisplayName("delete — 已结束活动 → 物理删除成功")
    void shouldDelete_whenActivityEnded() {
        when(flashSaleRepository.findById(FLASH_ID)).thenReturn(sale(
                LocalDateTime.now().minusHours(3), LocalDateTime.now().minusHours(1), 100, 100));

        flashSaleAdminManager.deleteFlashSale(FLASH_ID);

        verify(flashSaleRepository).deleteById(FLASH_ID);
    }

    @Test
    @DisplayName("delete — 未开始活动 → 物理删除成功")
    void shouldDelete_whenActivityNotStarted() {
        when(flashSaleRepository.findById(FLASH_ID)).thenReturn(sale(
                LocalDateTime.now().plusHours(1), LocalDateTime.now().plusHours(3), 100, 0));

        flashSaleAdminManager.deleteFlashSale(FLASH_ID);

        verify(flashSaleRepository).deleteById(FLASH_ID);
    }

    @Test
    @DisplayName("delete — 活动不存在 → NO_RESOURCE_FOUND(1103)")
    void shouldThrowNoResourceFound_whenDeleteMissingActivity() {
        when(flashSaleRepository.findById(FLASH_ID)).thenReturn(null);

        BizException ex = assertThrows(BizException.class,
                () -> flashSaleAdminManager.deleteFlashSale(FLASH_ID));

        assertEquals(ErrorCode.NO_RESOURCE_FOUND, ex.getErrorCode());
        verify(flashSaleRepository, never()).deleteById(any());
    }

    // ==================== 构造辅助 ====================

    private FlashSaleCreateReq createReq(LocalDateTime start, LocalDateTime end) {
        FlashSaleCreateReq req = new FlashSaleCreateReq();
        req.setProductId(10L);
        req.setSkuId(1000L);
        req.setFlashPrice(9900);
        req.setStock(100);
        req.setStartTime(start);
        req.setEndTime(end);
        return req;
    }

    private FlashSale sale(LocalDateTime start, LocalDateTime end, int stock, int soldCount) {
        return FlashSale.builder()
                .id(FLASH_ID)
                .productId(10L)
                .skuId(1000L)
                .flashPrice(9900)
                .stock(stock)
                .soldCount(soldCount)
                .status(FlashSaleStatusEnum.ACTIVE.getCode())
                .version(0)
                .startTime(start)
                .endTime(end)
                .build();
    }
}
