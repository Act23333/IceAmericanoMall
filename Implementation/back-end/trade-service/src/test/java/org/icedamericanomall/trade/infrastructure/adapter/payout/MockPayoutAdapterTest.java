package org.icedamericanomall.trade.infrastructure.adapter.payout;

import org.icedamericanomall.trade.domain.port.WithdrawalPayoutPort.PayoutCommand;
import org.icedamericanomall.trade.domain.port.WithdrawalPayoutPort.PayoutResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * mock 出款通道：四态 + 幂等 + 占位凭据自检。
 *
 * <p>时间全部由 {@link MutableClock} 推进，<b>没有一个 sleep</b>。
 */
@DisplayName("MockPayoutAdapter mock 出款通道")
class MockPayoutAdapterTest {

    private static final String PAYOUT_NO = "WD-1";
    private static final PayoutCommand COMMAND =
            new PayoutCommand(PAYOUT_NO, 1L, 2L, 4_000, "6222****", "某银行");

    @Test
    @DisplayName("ACCEPTED — 受理成功，channelRef 自曝 MOCK 身份（对账能分开）")
    void shouldAcceptAndExposeMockIdentity() {
        PayoutResult result = adapter(MockPayoutAdapter.Mode.ACCEPTED, Duration.ZERO).pay(COMMAND);

        assertTrue(result.accepted());
        assertTrue(result.channelRef().startsWith(MockPayoutAdapter.CHANNEL_PREFIX),
                "channelRef 必须带 MOCK- 前缀，否则对账时会被当成真实流水");
    }

    @Test
    @DisplayName("REJECTED — **不置已打款，保持付款中**（FAILED 不存在）")
    void shouldStayPaying_whenChannelRejects() {
        PayoutResult result = adapter(MockPayoutAdapter.Mode.REJECTED, Duration.ZERO).pay(COMMAND);

        assertFalse(result.accepted(), "未受理 ⇒ 调用方不得标已打款，实际停在付款中并告警");
        assertEquals("MOCK_REJECTED", result.reason());
    }

    @Test
    @DisplayName("TIMEOUT — **不置已打款，保持付款中**（结果未知 ≠ 失败）")
    void shouldStayPaying_whenChannelTimesOut() {
        PayoutResult result = adapter(MockPayoutAdapter.Mode.TIMEOUT, Duration.ZERO).pay(COMMAND);

        assertFalse(result.accepted());
        assertEquals("MOCK_TIMEOUT", result.reason(), "未知必须与「确认失败」区分开，否则会引出重试出两次钱");
    }

    @Test
    @DisplayName("ASYNC_CALLBACK — 时钟没走完 ⇒ 还没到；走完 ⇒ 同一 payoutNo 返回同一结果（幂等）")
    void shouldSettleAfterClockAdvances_andBeIdempotent() {
        MutableClock clock = new MutableClock();
        MockPayoutAdapter mock = new MockPayoutAdapter(
                MockPayoutAdapter.Mode.ASYNC_CALLBACK, Duration.ofSeconds(3), clock, blankCredentials());

        PayoutResult first = mock.pay(COMMAND);
        assertFalse(first.accepted());
        assertEquals("MOCK_AWAITING_CALLBACK", first.reason(), "回执没到之前，每次问都应该是「还没到」");

        clock.advance(Duration.ofSeconds(3));
        PayoutResult second = mock.pay(COMMAND);
        assertTrue(second.accepted());

        PayoutResult third = mock.pay(COMMAND);
        assertEquals(second.channelRef(), third.channelRef(), "同一 payoutNo 的终态结果必须冻结并原样返回");
        assertEquals(1, mock.settledCount(), "幂等：不得因为重试多出一次出款");
    }

    @Test
    @DisplayName("占位凭据 ⇒ 启动即失败（检测占位值，不是空值）")
    void shouldFailFast_whenCredentialIsPlaceholder() {
        Map<String, String> placeholder = new LinkedHashMap<>();
        placeholder.put("app-id", "CHANGE_ME");

        assertThrows(IllegalStateException.class, () -> new MockPayoutAdapter(
                MockPayoutAdapter.Mode.ACCEPTED, Duration.ZERO, Clock.systemUTC(), placeholder),
                "占位凭据必须在**构造期**就炸，而不是等运行时");
    }

    @Test
    @DisplayName("空凭据 ⇒ 启动通过（mock 模式下属合法）")
    void shouldStartWithBlankCredentials() {
        MockPayoutAdapter mock = new MockPayoutAdapter(
                MockPayoutAdapter.Mode.ACCEPTED, Duration.ZERO, Clock.systemUTC(), blankCredentials());
        mock.verifyNoPlaceholderCredentials();     // 空凭据：自检通过
    }

    private static MockPayoutAdapter adapter(MockPayoutAdapter.Mode mode, Duration delay) {
        return new MockPayoutAdapter(mode, delay, Clock.systemUTC(), blankCredentials());
    }

    private static Map<String, String> blankCredentials() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("app-id", "");
        map.put("mch-id", "");
        map.put("api-key", "");
        return map;
    }

    /** 可推进的时钟：mock 的「异步回执」只靠它驱动，测试不 sleep。 */
    private static final class MutableClock extends Clock {

        private Instant now = Instant.parse("2026-10-19T00:00:00Z");

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
