package org.icedamericanomall.pay.infrastructure.scheduler;

import org.icedamericanomall.pay.app.InvoiceLinkCompensationRetryManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * InvoiceLinkCompensationRetryJob 测试 —— 两个入口（XXL-Job 生产 / {@code @Scheduled} 开发）
 * 都只做触发，编排在 app 层 Manager 里；开关能真正关掉进程内入口。
 *
 * <p>被测点刻意只有「委托」与「开关」：循环逻辑的 8 条语义由
 * {@code InvoiceLinkCompensationRetryManagerTest} 与
 * {@code InvoiceLinkCompensationRetryH2Test} 覆盖，此处重复断言只会制造双份真相。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("InvoiceLinkCompensationRetryJob 调度入口测试")
class InvoiceLinkCompensationRetryJobTest {

    @Mock
    private InvoiceLinkCompensationRetryManager retryManager;

    @Test
    @DisplayName("XXL-Job 入口 — 委托 Manager 跑一轮（生产调度径）")
    void shouldDelegateToManager_whenXxlJobTriggers() {
        when(retryManager.retryDueCompensations()).thenReturn(3);
        InvoiceLinkCompensationRetryJob job = new InvoiceLinkCompensationRetryJob(retryManager, true);

        job.retryInvoiceLinkCompensation();

        verify(retryManager).retryDueCompensations();
    }

    @Test
    @DisplayName("定时入口（默认开）— 同样委托 Manager 跑一轮")
    void shouldDelegateToManager_whenScheduledEntryEnabled() {
        when(retryManager.retryDueCompensations()).thenReturn(0);
        InvoiceLinkCompensationRetryJob job = new InvoiceLinkCompensationRetryJob(retryManager, true);

        job.retryInvoiceLinkCompensationScheduled();

        verify(retryManager).retryDueCompensations();
    }

    @Test
    @DisplayName("定时入口关掉（pay.invoice.compensation.retry.scheduled-enabled=false）— 不跑，XXL 入口不受影响")
    void shouldSkipScheduledRound_whenScheduledEntryDisabled() {
        InvoiceLinkCompensationRetryJob job = new InvoiceLinkCompensationRetryJob(retryManager, false);
        when(retryManager.retryDueCompensations()).thenReturn(1);

        job.retryInvoiceLinkCompensationScheduled();
        verify(retryManager, never()).retryDueCompensations();

        job.retryInvoiceLinkCompensation();
        verify(retryManager, times(1)).retryDueCompensations();
    }
}
