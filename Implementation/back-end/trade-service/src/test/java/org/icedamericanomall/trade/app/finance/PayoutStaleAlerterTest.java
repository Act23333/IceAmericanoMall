package org.icedamericanomall.trade.app.finance;

import org.icedamericanomall.trade.domain.repository.WithdrawalRepository;
import org.icedamericanomall.trade.domain.repository.WithdrawalRepository.StalePaying;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

/** 停留告警：代次节奏 + 去重 + 告警体内容（含"下一步"，不含敏感信息）。时间由 MutableClock 推进，零 sleep。 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PayoutStaleAlerter 付款中停留告警")
class PayoutStaleAlerterTest {

    private static final int THRESHOLD_MINUTES = 30;
    private static final Instant T0 = Instant.parse("2026-10-19T00:00:00Z");

    @Mock private WithdrawalRepository withdrawalRepository;
    @Mock private WithdrawalPayoutService payoutService;

    @Test
    @DisplayName("停留未到阈值 ⇒ 不告警")
    void shouldNotAlert_whenDwellBelowThreshold() {
        MutableClock clock = new MutableClock();
        stubRows(clock, 20);
        assertEquals(0, alerter(clock).alertStale(100));
    }

    @Test
    @DisplayName("跨过阈值 ⇒ 告警一次；同代次再扫 ⇒ 去重不重复告")
    void shouldAlertOncePerGeneration() {
        MutableClock clock = new MutableClock();
        stubRows(clock, 45);
        PayoutStaleAlerter alerter = alerter(clock);
        assertEquals(1, alerter.alertStale(100), "首个窗口必须告");
        assertEquals(0, alerter.alertStale(100), "同一代次内不得重复告");
    }

    @Test
    @DisplayName("再跨一个窗口（代次 +1）⇒ 再告一次")
    void shouldAlertAgainAfterNextGeneration() {
        MutableClock clock = new MutableClock();
        stubRows(clock, 45);
        PayoutStaleAlerter alerter = alerter(clock);
        assertEquals(1, alerter.alertStale(100));
        clock.advance(Duration.ofMinutes(THRESHOLD_MINUTES));
        assertEquals(1, alerter.alertStale(100), "跨过 N 分钟 ⇒ 新代次 ⇒ 再告一次");
        assertEquals(2, alerter.alertedKeyCount());
    }

    @Test
    @DisplayName("告警体：带 payoutNo/停留时长/上次 pay 返回/下一步，不带账号与开户行")
    void alertBodyShouldCarryLocatingInfoButNoSecrets() {
        when(payoutService.lastResult("WD-1")).thenReturn("REJECTED(MOCK_REJECTED)");
        PayoutStaleAlerter alerter = new PayoutStaleAlerter(withdrawalRepository, new MutableClock(), payoutService);
        ReflectionTestUtils.setField(alerter, "staleMinutes", THRESHOLD_MINUTES);

        String body = alerter.body(row(), 45, 1);

        assertTrue(body.contains("\"payoutNo\":\"WD-1\""), "必须能定位到单");
        assertTrue(body.contains("\"dwellMinutes\":45"), "必须给停留时长");
        assertTrue(body.contains("REJECTED(MOCK_REJECTED)"), "必须给上次出款结果");
        assertTrue(body.contains("nextAction"), "必须给下一步做什么");
        assertFalse(body.contains("6222"), "不得带银行账号");
        assertFalse(body.contains("银行"), "不得带开户行");
    }

    private void stubRows(MutableClock clock, long dwellMinutes) {
        LocalDateTime created = LocalDateTime.ofInstant(
                clock.instant().minus(Duration.ofMinutes(dwellMinutes)), ZoneId.systemDefault());
        when(withdrawalRepository.findStalePaying(any(), anyInt()))
                .thenReturn(List.of(new StalePaying(7L, "WD-1", 9L, 4_000, created)));
    }

    private StalePaying row() {
        LocalDateTime created = LocalDateTime.ofInstant(T0.minus(Duration.ofMinutes(45)), ZoneId.systemDefault());
        return new StalePaying(7L, "WD-1", 9L, 4_000, created);
    }

    private PayoutStaleAlerter alerter(MutableClock clock) {
        PayoutStaleAlerter alerter = new PayoutStaleAlerter(withdrawalRepository, clock, payoutService);
        ReflectionTestUtils.setField(alerter, "staleMinutes", THRESHOLD_MINUTES);
        return alerter;
    }

    private static final class MutableClock extends Clock {

        private Instant now = T0;

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
