package org.icedamericanomall.wallet.infrastructure.scheduler;

import org.icedamericanomall.wallet.domain.model.currency.Currency;
import org.icedamericanomall.wallet.domain.model.currency.ExchangeRate;
import org.icedamericanomall.wallet.domain.repository.CurrencyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestOperations;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("汇率定时拉取 Job — 空配置跳过 / 定点整数转换 / 失败保留上次值（D2）")
@ExtendWith(MockitoExtension.class)
class ExchangeRatePullJobTest {

    private static final String API_URL = "https://open.er-api.com/v6/latest/CNY";

    @Mock
    private CurrencyRepository currencyRepository;

    @Mock
    private RestOperations restOperations;

    private ExchangeRatePullJob job;

    @BeforeEach
    void setUp() {
        job = new ExchangeRatePullJob(currencyRepository, API_URL, restOperations);
    }

    @Test
    @DisplayName("pullOnce — api-url 未配置（空串）→ 跳过，不打汇率源也不落库")
    void shouldSkip_whenApiUrlBlank() {
        ExchangeRatePullJob blankUrlJob = new ExchangeRatePullJob(currencyRepository, "  ", restOperations);

        assertDoesNotThrow(blankUrlJob::pullDaily);

        verifyNoInteractions(restOperations);
        verify(currencyRepository, never()).updateRate(any());
    }

    @Test
    @DisplayName("pullOnce — 拉取成功：小数文本转定点 long（×10⁶），source=API、updaterId=null")
    void shouldConvertToScaledLong_whenPullSucceeds() {
        when(currencyRepository.listEnabledWithRate()).thenReturn(List.of(enabled("USD"), enabled("JPY")));
        when(restOperations.getForObject(eq(API_URL), eq(String.class)))
                .thenReturn("{\"base_code\":\"CNY\",\"rates\":{\"USD\":0.1403,\"JPY\":20.91}}");

        assertDoesNotThrow(job::pullDaily);

        ArgumentCaptor<ExchangeRate> captor = ArgumentCaptor.forClass(ExchangeRate.class);
        verify(currencyRepository, org.mockito.Mockito.times(2)).updateRate(captor.capture());
        Map<String, ExchangeRate> byCode = captor.getAllValues().stream()
                .collect(Collectors.toMap(ExchangeRate::code, Function.identity()));
        assertEquals(140300L, byCode.get("USD").rateScaled(), "USD 0.1403 → 140300");
        assertEquals(20910000L, byCode.get("JPY").rateScaled(), "JPY 20.91 → 20910000（无精度损失）");
        byCode.values().forEach(rate -> {
            assertEquals(ExchangeRate.SOURCE_API, rate.source());
            assertNull(rate.updaterId(), "API 拉取不记修正人");
            assertTrue(rate.effectTime().isBefore(LocalDateTime.now().plusSeconds(1)));
        });
    }

    @Test
    @DisplayName("pullDaily — HTTP 失败 → 全部保留上次值，不向调度器外抛异常")
    void shouldKeepAllLastValues_whenHttpFails() {
        when(currencyRepository.listEnabledWithRate()).thenReturn(List.of(enabled("USD")));
        when(restOperations.getForObject(eq(API_URL), eq(String.class)))
                .thenThrow(new RestClientException("connection refused"));

        assertDoesNotThrow(job::pullDaily);

        verify(currencyRepository, never()).updateRate(any());
    }

    @Test
    @DisplayName("pullDaily — 响应缺少 rates 对象（形态不符）→ 全部保留上次值")
    void shouldKeepAllLastValues_whenRatesNodeMissing() {
        when(currencyRepository.listEnabledWithRate()).thenReturn(List.of(enabled("USD")));
        when(restOperations.getForObject(eq(API_URL), eq(String.class))).thenReturn("{\"result\":\"ok\"}");

        assertDoesNotThrow(job::pullDaily);

        verify(currencyRepository, never()).updateRate(any());
    }

    @Test
    @DisplayName("pullOnce — 单币汇率值非法（负数）→ 该币保留上次值，其余币种正常更新")
    void shouldKeepLastPerCurrency_whenValueNegative() {
        when(currencyRepository.listEnabledWithRate()).thenReturn(List.of(enabled("USD"), enabled("JPY")));
        when(restOperations.getForObject(eq(API_URL), eq(String.class)))
                .thenReturn("{\"rates\":{\"USD\":0.1403,\"JPY\":-20.91}}");

        job.pullDaily();

        ArgumentCaptor<ExchangeRate> captor = ArgumentCaptor.forClass(ExchangeRate.class);
        verify(currencyRepository).updateRate(captor.capture());
        assertEquals("USD", captor.getValue().code());
        assertEquals(140300L, captor.getValue().rateScaled());
    }

    @Test
    @DisplayName("pullOnce — 单币汇率值溢出定点 long → 该币保留上次值（longValueExact 防静默截断）")
    void shouldKeepLastPerCurrency_whenValueOverflows() {
        when(currencyRepository.listEnabledWithRate()).thenReturn(List.of(enabled("USD"), enabled("JPY")));
        when(restOperations.getForObject(eq(API_URL), eq(String.class)))
                .thenReturn("{\"rates\":{\"USD\":0.1403,\"JPY\":1e30}}");

        job.pullDaily();

        ArgumentCaptor<ExchangeRate> captor = ArgumentCaptor.forClass(ExchangeRate.class);
        verify(currencyRepository).updateRate(captor.capture());
        assertEquals("USD", captor.getValue().code());
    }

    @Test
    @DisplayName("pullOnce — 汇率源缺少某启用币种报价 → 该币保留上次值（D2）")
    void shouldKeepLast_whenSourceMissingCurrency() {
        when(currencyRepository.listEnabledWithRate()).thenReturn(List.of(enabled("USD"), enabled("KRW")));
        when(restOperations.getForObject(eq(API_URL), eq(String.class)))
                .thenReturn("{\"rates\":{\"USD\":0.1403}}");

        job.pullDaily();

        ArgumentCaptor<ExchangeRate> captor = ArgumentCaptor.forClass(ExchangeRate.class);
        verify(currencyRepository).updateRate(captor.capture());
        assertEquals("USD", captor.getValue().code());
    }

    @Test
    @DisplayName("pullOnce — 无启用币种 → 不打汇率源")
    void shouldSkip_whenNoEnabledCurrency() {
        when(currencyRepository.listEnabledWithRate()).thenReturn(List.of());

        job.pullDaily();

        verify(restOperations, never()).getForObject(anyString(), eq(String.class));
        verify(currencyRepository, never()).updateRate(any());
    }

    private static Currency enabled(String code) {
        return new Currency(code, code, "$", 2, Currency.STATUS_ENABLED,
                new ExchangeRate(code, 140300, ExchangeRate.SOURCE_MANUAL, LocalDateTime.now(), null, null));
    }
}
