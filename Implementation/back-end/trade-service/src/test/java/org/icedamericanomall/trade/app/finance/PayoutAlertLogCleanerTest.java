package org.icedamericanomall.trade.app.finance;

import org.icedamericanomall.trade.domain.repository.WithdrawalRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 告警日志清理用例（仓储全桩）。
 *
 * <p>固定时钟是重点：保留期边界一旦用真实时间算，用例会随运行日期漂移，
 * 变成"今天绿明天红"的东西。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("告警日志保留期清理")
class PayoutAlertLogCleanerTest {

    private static final Clock FIXED = Clock.fixed(
            LocalDateTime.of(2026, 10, 19, 3, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC);

    @Mock
    private WithdrawalRepository withdrawalRepository;

    @InjectMocks
    private PayoutAlertLogCleaner cleaner;

    @Test
    @DisplayName("清理边界 = 当前时刻减去保留期，且只透传给仓储")
    void shouldPurgeBeforeRetentionCutoff() {
        when(withdrawalRepository.purgeAlertLogBefore(any())).thenReturn(7);

        int deleted = cleaner.purgeExpired(FIXED);

        assertEquals(7, deleted, "返回值就是仓储删除的行数");
        ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(withdrawalRepository).purgeAlertLogBefore(captor.capture());
        assertEquals(FIXED.instant().atZone(ZoneOffset.UTC).toLocalDateTime()
                        .minusDays(PayoutAlertLogCleaner.RETENTION_DAYS), captor.getValue(),
                "边界必须是「固定时刻 − 保留期」，不能用真实时间");
    }

    @Test
    @DisplayName("没有可删记录时返回零，不抛异常")
    void shouldReturnZero_whenNothingToPurge() {
        when(withdrawalRepository.purgeAlertLogBefore(any())).thenReturn(0);

        assertEquals(0, cleaner.purgeExpired(FIXED));
    }
}
