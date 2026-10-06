package org.icedamericanomall.wallet.app.currency;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.common.security.UserContext;
import org.icedamericanomall.common.security.UserInfo;
import org.icedamericanomall.wallet.adapter.request.RateUpdateReq;
import org.icedamericanomall.wallet.adapter.request.StatusUpdateReq;
import org.icedamericanomall.wallet.adapter.response.CurrencyAdminVO;
import org.icedamericanomall.wallet.domain.model.currency.Currency;
import org.icedamericanomall.wallet.domain.model.currency.ExchangeRate;
import org.icedamericanomall.wallet.domain.repository.CurrencyRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
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

@DisplayName("币种管理编排 — AD66 联合分页 / AD67 人工修正 / AD68 启停")
@ExtendWith(MockitoExtension.class)
class CurrencyAdminManagerTest {

    private static final LocalDateTime EFFECT_TIME = LocalDateTime.of(2026, 10, 5, 3, 0);

    @Mock
    private CurrencyRepository currencyRepository;

    @Mock
    private CurrencyQueryManager currencyQueryManager;

    @InjectMocks
    private CurrencyAdminManager currencyAdminManager;

    @BeforeEach
    void setUpAdminContext() {
        UserContext.setUser(UserInfo.builder().userId(9L).build());
    }

    @AfterEach
    void clearAdminContext() {
        UserContext.removeUser();
    }

    @Test
    @DisplayName("page — 联合分页映射管理端视图（含 source/effectTime/updaterId 留痕）")
    void shouldMapPageWithSourceAndEffectTime() {
        Page<Currency> domainPage = new Page<>(1, 20);
        domainPage.setRecords(List.of(new Currency("USD", "美元", "$", 2, Currency.STATUS_ENABLED,
                new ExchangeRate("USD", 140300, ExchangeRate.SOURCE_MANUAL, EFFECT_TIME, 9L, "人工修正"))));
        when(currencyRepository.pageWithRate(null, 1, 20)).thenReturn(domainPage);

        Page<CurrencyAdminVO> result = (Page<CurrencyAdminVO>) currencyAdminManager.page(null, 1, 20);

        assertEquals(1, result.getRecords().size());
        CurrencyAdminVO vo = result.getRecords().get(0);
        assertEquals("USD", vo.getCode());
        assertEquals(2, vo.getExponent());
        assertEquals(Currency.STATUS_ENABLED, vo.getStatus());
        assertEquals(140300L, vo.getRateScaled());
        assertEquals(ExchangeRate.SOURCE_MANUAL, vo.getSource());
        assertEquals(EFFECT_TIME, vo.getEffectTime());
        assertEquals(9L, vo.getUpdaterId());
        assertEquals("人工修正", vo.getRemark());
    }

    @Test
    @DisplayName("updateRate — 定点整数落库 source=MANUAL + updater_id=UserContext + effectTime=now")
    void shouldUpsertManualRateWithUpdater_whenRateUpdated() {
        LocalDateTime before = LocalDateTime.now();
        when(currencyRepository.findByCode("USD")).thenReturn(enabledUsd());

        currencyAdminManager.updateRate("USD", reqOf(140300L, "人工修正"));

        ArgumentCaptor<ExchangeRate> captor = ArgumentCaptor.forClass(ExchangeRate.class);
        verify(currencyRepository).updateRate(captor.capture());
        ExchangeRate rate = captor.getValue();
        assertEquals("USD", rate.code());
        assertEquals(140300L, rate.rateScaled());
        assertEquals(ExchangeRate.SOURCE_MANUAL, rate.source());
        assertEquals(9L, rate.updaterId());
        assertEquals("人工修正", rate.remark());
        assertTrue(rate.effectTime().isAfter(before), "生效时间=修正时刻");
        verify(currencyQueryManager).evictEnabledListCache();
    }

    @Test
    @DisplayName("updateRate — 币种不存在 → NO_RESOURCE_FOUND(1103)，不落库")
    void shouldThrow1103_whenRateCurrencyAbsent() {
        when(currencyRepository.findByCode("XXX")).thenReturn(null);

        BizException ex = assertThrows(BizException.class,
                () -> currencyAdminManager.updateRate("XXX", reqOf(140300L, null)));

        assertEquals(ErrorCode.NO_RESOURCE_FOUND, ex.getErrorCode());
        verify(currencyRepository, never()).updateRate(any());
        verifyNoInteractions(currencyQueryManager);
    }

    @Test
    @DisplayName("updateRate — 汇率非正数 → PARAM_ERROR（聚合恒正不变式守卫），不落库")
    void shouldReject_whenRateNotPositive() {
        when(currencyRepository.findByCode("USD")).thenReturn(enabledUsd());

        BizException ex = assertThrows(BizException.class,
                () -> currencyAdminManager.updateRate("USD", reqOf(0L, null)));

        assertEquals(ErrorCode.PARAM_ERROR, ex.getErrorCode());
        verify(currencyRepository, never()).updateRate(any());
    }

    @Test
    @DisplayName("updateStatus — 条件更新命中 → 停用落库并失效缓存（既有快照展示不受影响）")
    void shouldUpdateStatus_whenHit() {
        when(currencyRepository.updateStatus("USD", Currency.STATUS_DISABLED)).thenReturn(true);

        currencyAdminManager.updateStatus("USD", statusReqOf(Currency.STATUS_DISABLED));

        verify(currencyRepository).updateStatus("USD", Currency.STATUS_DISABLED);
        verify(currencyQueryManager).evictEnabledListCache();
    }

    @Test
    @DisplayName("updateStatus — 币种不存在（条件更新未命中）→ NO_RESOURCE_FOUND(1103)")
    void shouldThrow1103_whenStatusUpdateMissed() {
        when(currencyRepository.updateStatus("XXX", Currency.STATUS_ENABLED)).thenReturn(false);

        BizException ex = assertThrows(BizException.class,
                () -> currencyAdminManager.updateStatus("XXX", statusReqOf(Currency.STATUS_ENABLED)));

        assertEquals(ErrorCode.NO_RESOURCE_FOUND, ex.getErrorCode());
    }

    @Test
    @DisplayName("updateStatus — 状态越界（2）→ PARAM_ERROR，不打仓储")
    void shouldRejectStatus_whenOutOfRange() {
        BizException ex = assertThrows(BizException.class,
                () -> currencyAdminManager.updateStatus("USD", statusReqOf(2)));

        assertEquals(ErrorCode.PARAM_ERROR, ex.getErrorCode());
        verify(currencyRepository, never()).updateStatus(any(), org.mockito.ArgumentMatchers.anyInt());
    }

    // ==================== 测试夹具 ====================

    private static Currency enabledUsd() {
        return new Currency("USD", "美元", "$", 2, Currency.STATUS_ENABLED,
                new ExchangeRate("USD", 140300, ExchangeRate.SOURCE_API, EFFECT_TIME, null, null));
    }

    private static RateUpdateReq reqOf(Long rateScaled, String remark) {
        RateUpdateReq req = new RateUpdateReq();
        req.setRateScaled(rateScaled);
        req.setRemark(remark);
        return req;
    }

    private static StatusUpdateReq statusReqOf(Integer status) {
        StatusUpdateReq req = new StatusUpdateReq();
        req.setStatus(status);
        return req;
    }
}
