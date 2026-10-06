package org.icedamericanomall.wallet.adapter;

import org.icedamericanomall.dto.CurrencyRateCO;
import org.icedamericanomall.wallet.adapter.internal.InternalCurrencyController;
import org.icedamericanomall.wallet.app.currency.CurrencyQueryManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link InternalCurrencyController} 委托测试（I61）——裸 CO 返回不包 Result，
 * 未启用/未配置的 1103 语义由编排抛出、全局异常处理器按 {@code /internal/**}
 * 极简透出（ADR-004），此处验证委托与参数透传。
 */
@DisplayName("I61 币种内部端点 — 委托与裸 CO 返回")
@ExtendWith(MockitoExtension.class)
class InternalCurrencyControllerTest {

    @Mock
    private CurrencyQueryManager currencyQueryManager;

    @InjectMocks
    private InternalCurrencyController internalCurrencyController;

    @Test
    @DisplayName("GET /internal/currency/current — code 透传，返回裸 CurrencyRateCO")
    void shouldDelegateAndReturnBareCO_whenCurrent() {
        CurrencyRateCO co = new CurrencyRateCO("USD", 140300L, 2);
        when(currencyQueryManager.currentRate("USD")).thenReturn(co);

        CurrencyRateCO result = internalCurrencyController.current("USD");

        assertSame(co, result);
        assertEquals(140300L, result.getRateScaled());
        verify(currencyQueryManager).currentRate("USD");
    }
}
