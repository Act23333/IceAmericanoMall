package org.icedamericanomall.wallet.app.currency;

import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BadRequestException;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.dto.CurrencyRateCO;
import org.icedamericanomall.wallet.adapter.response.CurrencyVO;
import org.icedamericanomall.wallet.domain.model.currency.Currency;
import org.icedamericanomall.wallet.domain.model.currency.ExchangeRate;
import org.icedamericanomall.wallet.domain.repository.CurrencyRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("币种查询编排 — P36 列表（60s 缓存/fail-open）+ I61 当前汇率（1103）")
@ExtendWith(MockitoExtension.class)
class CurrencyQueryManagerTest {

    private static final LocalDateTime EFFECT_TIME = LocalDateTime.of(2026, 10, 5, 3, 0);

    @Mock
    private CurrencyRepository currencyRepository;

    @InjectMocks
    private CurrencyQueryManager currencyQueryManager;

    @Test
    @DisplayName("listEnabled — 返回启用币种并映射三要素+定点汇率；未配置汇率的币种 rate 字段为 null")
    void shouldListEnabledWithRateFields_whenCacheMiss() {
        when(currencyRepository.listEnabledWithRate()).thenReturn(List.of(
                new Currency("USD", "美元", "$", 2, Currency.STATUS_ENABLED,
                        new ExchangeRate("USD", 140300, ExchangeRate.SOURCE_MANUAL, EFFECT_TIME, null, null)),
                new Currency("JPY", "日元", "¥", 0, Currency.STATUS_ENABLED, null)));

        List<CurrencyVO> list = currencyQueryManager.listEnabled();

        assertEquals(2, list.size());
        CurrencyVO usd = list.get(0);
        assertEquals("USD", usd.getCode());
        assertEquals("美元", usd.getName());
        assertEquals("$", usd.getSymbol());
        assertEquals(2, usd.getExponent());
        assertEquals(140300L, usd.getRateScaled());
        assertEquals(EFFECT_TIME, usd.getEffectTime());
        assertNull(list.get(1).getRateScaled(), "未配置汇率的币种 rate 字段为 null（前端回落 CNY）");
    }

    @Test
    @DisplayName("listEnabled — 60s 缓存命中：第二次调用不再打仓储")
    void shouldHitCache_whenListCalledTwice() {
        when(currencyRepository.listEnabledWithRate()).thenReturn(List.of(
                new Currency("USD", "美元", "$", 2, Currency.STATUS_ENABLED, null)));

        List<CurrencyVO> first = currencyQueryManager.listEnabled();
        List<CurrencyVO> second = currencyQueryManager.listEnabled();

        assertEquals(first, second);
        verify(currencyRepository, times(1)).listEnabledWithRate();
    }

    @Test
    @DisplayName("listEnabled — 写后失效：evict 后下一次调用重新加载")
    void shouldReloadAfterCacheEvict() {
        when(currencyRepository.listEnabledWithRate()).thenReturn(List.of(
                new Currency("USD", "美元", "$", 2, Currency.STATUS_ENABLED, null)));

        currencyQueryManager.listEnabled();
        currencyQueryManager.evictEnabledListCache();
        currencyQueryManager.listEnabled();

        verify(currencyRepository, times(2)).listEnabledWithRate();
    }

    @Test
    @DisplayName("listEnabled — 本地表读失败 → 空列表（展示型查询 fail-open，前端回落 CNY）")
    void shouldReturnEmptyList_whenRepositoryFails() {
        when(currencyRepository.listEnabledWithRate()).thenThrow(new RuntimeException("db down"));

        List<CurrencyVO> list = currencyQueryManager.listEnabled();

        assertEquals(List.of(), list);
    }

    @Test
    @DisplayName("currentRate — 已启用且汇率已配置 → 三字段契约对象")
    void shouldReturnThreeFields_whenRateConfigured() {
        when(currencyRepository.findByCode("USD")).thenReturn(new Currency("USD", "美元", "$", 2,
                Currency.STATUS_ENABLED,
                new ExchangeRate("USD", 140300, ExchangeRate.SOURCE_MANUAL, EFFECT_TIME, null, null)));

        CurrencyRateCO co = currencyQueryManager.currentRate("USD");

        assertEquals("USD", co.getCode());
        assertEquals(140300L, co.getRateScaled());
        assertEquals(2, co.getExponent());
    }

    @Test
    @DisplayName("currentRate — 币种不存在 → NO_RESOURCE_FOUND(1103)，trade 侧 fail-open 消化")
    void shouldThrow1103_whenCurrencyAbsent() {
        when(currencyRepository.findByCode("XXX")).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> currencyQueryManager.currentRate("XXX"));

        assertEquals(ErrorCode.NO_RESOURCE_FOUND, ex.getErrorCode());
    }

    @Test
    @DisplayName("currentRate — 币种已停用 → NO_RESOURCE_FOUND(1103)")
    void shouldThrow1103_whenCurrencyDisabled() {
        when(currencyRepository.findByCode("USD")).thenReturn(new Currency("USD", "美元", "$", 2,
                Currency.STATUS_DISABLED,
                new ExchangeRate("USD", 140300, ExchangeRate.SOURCE_API, EFFECT_TIME, null, null)));

        BizException ex = assertThrows(BizException.class, () -> currencyQueryManager.currentRate("USD"));

        assertEquals(ErrorCode.NO_RESOURCE_FOUND, ex.getErrorCode());
    }

    @Test
    @DisplayName("currentRate — 币种启用但汇率未配置 → NO_RESOURCE_FOUND(1103)")
    void shouldThrow1103_whenRateMissing() {
        when(currencyRepository.findByCode("JPY")).thenReturn(new Currency("JPY", "日元", "¥", 0,
                Currency.STATUS_ENABLED, null));

        BizException ex = assertThrows(BizException.class, () -> currencyQueryManager.currentRate("JPY"));

        assertEquals(ErrorCode.NO_RESOURCE_FOUND, ex.getErrorCode());
    }

    @Test
    @DisplayName("currentRate — 币种代码为空 → BadRequestException，不打仓储")
    void shouldReject_whenCodeBlank() {
        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> currencyQueryManager.currentRate(" "));

        assertEquals(ErrorCode.PARAM_ERROR, ex.getErrorCode());
    }
}
