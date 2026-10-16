package org.icedamericanomall.marketing.app.flash;

import org.icedamericanomall.common.security.UserContext;
import org.icedamericanomall.common.security.UserInfo;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.marketing.app.dto.FlashBuyResult;
import org.icedamericanomall.marketing.domain.enums.FlashSaleStatusEnum;
import org.icedamericanomall.marketing.domain.model.flash.FlashSale;
import org.icedamericanomall.marketing.domain.port.FlashStockStore;
import org.icedamericanomall.marketing.domain.repository.FlashSaleRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * FlashSaleManager 状态迁移与预扣分支单元测试。
 *
 * <p>覆盖：场次不存在 / 未开始 / 售罄 / 超限购 / 未知失败 / 预扣成功（剩余库存按 DB 口径传入 Redis）。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("FlashSaleManager 预扣状态迁移单元测试")
class FlashSaleManagerTest {

    private static final Long FLASH_ID = 1L;
    private static final Long ADDRESS_ID = 33L;
    private static final Long USER_ID = 7L;

    @Mock FlashSaleRepository flashSaleRepository;
    @Mock FlashStockStore flashStockStore;

    private FlashSaleManager flashSaleManager;

    @BeforeEach
    void setUp() {
        flashSaleManager = new FlashSaleManager(flashSaleRepository, flashStockStore);
        UserContext.setUser(UserInfo.builder().userId(USER_ID).build());
    }

    @AfterEach
    void tearDown() {
        UserContext.removeUser();
    }

    @Test
    @DisplayName("buy — 场次不存在 → FLASH_SALE_NOT_FOUND，不触碰 Redis")
    void shouldThrowNotFound_whenFlashSaleMissing() {
        when(flashSaleRepository.findById(FLASH_ID)).thenReturn(null);

        BizException ex = assertThrows(BizException.class,
                () -> flashSaleManager.buy(FLASH_ID, ADDRESS_ID));

        assertEquals(ErrorCode.FLASH_SALE_NOT_FOUND, ex.getErrorCode());
        verifyNoInteractions(flashStockStore);
    }

    @Test
    @DisplayName("buy — 场次非进行中 → FLASH_SALE_NOT_STARTED，不触碰 Redis")
    void shouldThrowNotStarted_whenStatusNotActive() {
        when(flashSaleRepository.findById(FLASH_ID)).thenReturn(
                flashSale(FlashSaleStatusEnum.PENDING.getCode(), 100, 0));

        BizException ex = assertThrows(BizException.class,
                () -> flashSaleManager.buy(FLASH_ID, ADDRESS_ID));

        assertEquals(ErrorCode.FLASH_SALE_NOT_STARTED, ex.getErrorCode());
        verifyNoInteractions(flashStockStore);
    }

    @Test
    @DisplayName("buy — Redis 全分片售罄 → FLASH_SALE_SOLD_OUT")
    void shouldThrowSoldOut_whenStoreSoldOut() {
        when(flashSaleRepository.findById(FLASH_ID)).thenReturn(active(100, 0));
        when(flashStockStore.tryDeduct(eq(FLASH_ID), eq(USER_ID), eq(1), eq(100), any()))
                .thenReturn(FlashStockStore.SOLD_OUT);

        BizException ex = assertThrows(BizException.class,
                () -> flashSaleManager.buy(FLASH_ID, ADDRESS_ID));

        assertEquals(ErrorCode.FLASH_SALE_SOLD_OUT, ex.getErrorCode());
    }

    @Test
    @DisplayName("buy — 已达限购 → FLASH_SALE_LIMIT_EXCEEDED")
    void shouldThrowLimitExceeded_whenStoreLimitExceeded() {
        when(flashSaleRepository.findById(FLASH_ID)).thenReturn(active(100, 0));
        when(flashStockStore.tryDeduct(eq(FLASH_ID), eq(USER_ID), eq(1), eq(100), any()))
                .thenReturn(FlashStockStore.LIMIT_EXCEEDED);

        BizException ex = assertThrows(BizException.class,
                () -> flashSaleManager.buy(FLASH_ID, ADDRESS_ID));

        assertEquals(ErrorCode.FLASH_SALE_LIMIT_EXCEEDED, ex.getErrorCode());
    }

    @Test
    @DisplayName("buy — 未知负值 → FLASH_SALE_FAILED")
    void shouldThrowFailed_whenStoreReturnsUnknownNegative() {
        when(flashSaleRepository.findById(FLASH_ID)).thenReturn(active(100, 0));
        when(flashStockStore.tryDeduct(eq(FLASH_ID), eq(USER_ID), eq(1), eq(100), any())).thenReturn(-9L);

        BizException ex = assertThrows(BizException.class,
                () -> flashSaleManager.buy(FLASH_ID, ADDRESS_ID));

        assertEquals(ErrorCode.FLASH_SALE_FAILED, ex.getErrorCode());
    }

    @Test
    @DisplayName("buy — 预扣成功 → 返回场次快照与剩余库存，orderNo 留给编排层回填")
    void shouldReturnPreResult_whenDeductSucceeds() {
        when(flashSaleRepository.findById(FLASH_ID)).thenReturn(active(100, 30));
        when(flashStockStore.tryDeduct(eq(FLASH_ID), eq(USER_ID), eq(1), eq(70), any())).thenReturn(69L);

        FlashBuyResult result = flashSaleManager.buy(FLASH_ID, ADDRESS_ID);

        assertTrue(result.getSuccess());
        assertEquals(FLASH_ID, result.getFlashId());
        assertEquals(9900, result.getFlashPrice().intValue());
        assertEquals(69L, result.getRemainingStock().longValue());
        assertNull(result.getOrderNo(), "orderNo 由 MarketingManager 落单后回填");
    }

    private FlashSale active(int stock, int soldCount) {
        return flashSale(FlashSaleStatusEnum.ACTIVE.getCode(), stock, soldCount);
    }

    private FlashSale flashSale(int status, int stock, int soldCount) {
        return FlashSale.builder()
                .id(FLASH_ID)
                .spuId(100L)
                .skuId(1000L)
                .flashPrice(9900)
                .stock(stock)
                .soldCount(soldCount)
                .status(status)
                .version(0)
                .startTime(LocalDateTime.now().minusHours(1))
                .endTime(LocalDateTime.now().plusHours(1))
                .build();
    }
}
