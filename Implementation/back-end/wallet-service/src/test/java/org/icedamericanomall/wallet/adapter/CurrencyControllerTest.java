package org.icedamericanomall.wallet.adapter;

import org.icedamericanomall.common.api.Result;
import org.icedamericanomall.wallet.adapter.response.CurrencyVO;
import org.icedamericanomall.wallet.adapter.web.CurrencyController;
import org.icedamericanomall.wallet.app.currency.CurrencyQueryManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link CurrencyController} 委托测试——控制器只做参数校验 + 委托 + Result 包装，
 * 编排在 {@code CurrencyQueryManager}（P36）。
 */
@DisplayName("P36 币种公开端点 — 委托与 Result 包装")
@ExtendWith(MockitoExtension.class)
class CurrencyControllerTest {

    @Mock
    private CurrencyQueryManager currencyQueryManager;

    @InjectMocks
    private CurrencyController currencyController;

    @Test
    @DisplayName("GET /api/currency/list — 委托查询编排并包装 Result")
    void shouldDelegateAndWrap_whenList() {
        CurrencyVO vo = new CurrencyVO("USD", "美元", "$", 2, 140300L, null);
        when(currencyQueryManager.listEnabled()).thenReturn(List.of(vo));

        Result<List<CurrencyVO>> result = currencyController.list();

        assertEquals(Result.SUCCESS_CODE, result.getCode());
        assertEquals(1, result.getData().size());
        assertSame(vo, result.getData().get(0));
        verify(currencyQueryManager).listEnabled();
    }
}
