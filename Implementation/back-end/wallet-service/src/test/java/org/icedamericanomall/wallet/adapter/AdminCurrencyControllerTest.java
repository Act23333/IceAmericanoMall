package org.icedamericanomall.wallet.adapter;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.icedamericanomall.common.api.Result;
import org.icedamericanomall.wallet.adapter.admin.AdminCurrencyController;
import org.icedamericanomall.wallet.adapter.request.RateUpdateReq;
import org.icedamericanomall.wallet.adapter.request.StatusUpdateReq;
import org.icedamericanomall.wallet.adapter.response.CurrencyAdminVO;
import org.icedamericanomall.wallet.app.currency.CurrencyAdminManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AdminCurrencyController} 委托测试（AD66-68）——控制器只做参数校验 + 委托 +
 * Result 包装；审计由 {@code @OperationLog}（ADR-032 管道一）承载。
 */
@DisplayName("AD66-68 币种管理端 — 委托与 Result 包装")
@ExtendWith(MockitoExtension.class)
class AdminCurrencyControllerTest {

    @Mock
    private CurrencyAdminManager currencyAdminManager;

    @InjectMocks
    private AdminCurrencyController adminCurrencyController;

    @Test
    @DisplayName("GET /api/admin/currency/page — 过滤与分页参数透传，Result 包装")
    void shouldDelegateWithParams_whenPage() {
        Page<CurrencyAdminVO> domainPage = new Page<>(1, 20);
        when(currencyAdminManager.page(1, 2, 15)).thenReturn(domainPage);

        Result<?> result = adminCurrencyController.page(1, 2, 15);

        assertEquals(Result.SUCCESS_CODE, result.getCode());
        assertSame(domainPage, result.getData());
        verify(currencyAdminManager).page(1, 2, 15);
    }

    @Test
    @DisplayName("PUT /api/admin/currency/{code}/rate — 路径 code 与请求体透传，返回 Result.ok")
    void shouldDelegate_whenUpdateRate() {
        RateUpdateReq req = new RateUpdateReq();
        req.setRateScaled(140300L);
        req.setRemark("人工修正");

        Result<Void> result = adminCurrencyController.updateRate("USD", req);

        assertEquals(Result.SUCCESS_CODE, result.getCode());
        assertNull(result.getData());
        verify(currencyAdminManager).updateRate("USD", req);
    }

    @Test
    @DisplayName("PUT /api/admin/currency/{code}/status — 路径 code 与请求体透传，返回 Result.ok")
    void shouldDelegate_whenUpdateStatus() {
        StatusUpdateReq req = new StatusUpdateReq();
        req.setStatus(0);

        Result<Void> result = adminCurrencyController.updateStatus("USD", req);

        assertEquals(Result.SUCCESS_CODE, result.getCode());
        verify(currencyAdminManager).updateStatus("USD", req);
    }
}
