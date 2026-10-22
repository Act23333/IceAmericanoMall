package org.icedamericanomall.trade.infrastructure.adapter.payout;

import org.icedamericanomall.trade.domain.port.WithdrawalPayoutPort.PayoutCommand;
import org.icedamericanomall.trade.domain.port.WithdrawalPayoutPort.PayoutQueryResult;
import org.icedamericanomall.trade.domain.port.WithdrawalPayoutPort.PayoutResult;
import org.icedamericanomall.trade.domain.port.WithdrawalPayoutPort.QueryState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 查询契约用例：**「未知」不得被报成「失败」**。
 *
 * <p>这是 F2 分层处置的地基 —— 若适配器把"超时/等回调"报成 {@code FAILED}，
 * 上层的"未知态禁止自动解冻"就形同虚设：钱可能已出，账却被解冻重发。
 */
@DisplayName("出款查询契约（未知 ≠ 失败）")
class PayoutQueryContractTest {

    private static final String PAYOUT_NO = "9f3ac1b27e0d4a56-1";
    private static final PayoutCommand COMMAND =
            new PayoutCommand(PAYOUT_NO, 1L, 2L, 10_000, "6222", "招商银行");

    @Test
    @DisplayName("人工渠道：无渠道可查 ⇒ 必须 UNKNOWN（不是 SUCCESS，也不是抛异常）")
    void manualShouldReportUnknown() {
        PayoutQueryResult result = new ManualPayoutAdapter().query(PAYOUT_NO);

        assertEquals(QueryState.UNKNOWN, result.state(),
                "人工渠道没有可查状态：报 SUCCESS 会凭空断定到账，抛异常会让查询任务每次都红");
        assertEquals("MANUAL", result.channelRef());
    }

    @Test
    @DisplayName("mock 超时：查询必须报 UNKNOWN，不能报 FAILED")
    void timeoutShouldReportUnknown() {
        MockPayoutAdapter adapter = mock(MockPayoutAdapter.Mode.TIMEOUT, Duration.ZERO, Clock.systemUTC());

        assertEquals(QueryState.UNKNOWN, adapter.query(PAYOUT_NO).state(),
                "超时是「不知道」，报成失败会导致解冻后重发同一笔");
    }

    @Test
    @DisplayName("mock 明确拒绝：查询报 FAILED（可换号重发）")
    void rejectedShouldReportFailed() {
        MockPayoutAdapter adapter = mock(MockPayoutAdapter.Mode.REJECTED, Duration.ZERO, Clock.systemUTC());

        assertEquals(QueryState.FAILED, adapter.query(PAYOUT_NO).state());
    }

    @Test
    @DisplayName("mock 受理：查询报 SUCCESS")
    void acceptedShouldReportSuccess() {
        MockPayoutAdapter adapter = mock(MockPayoutAdapter.Mode.ACCEPTED, Duration.ZERO, Clock.systemUTC());
        adapter.pay(COMMAND);

        assertEquals(QueryState.SUCCESS, adapter.query(PAYOUT_NO).state());
    }

    @Test
    @DisplayName("mock 异步：回调未到 ⇒ UNKNOWN；到点后 ⇒ SUCCESS")
    void asyncShouldMoveFromUnknownToSuccess() {
        MutableClock clock = new MutableClock(Instant.parse("2026-10-19T00:00:00Z"));
        MockPayoutAdapter adapter = mock(MockPayoutAdapter.Mode.ASYNC_CALLBACK, Duration.ofMinutes(5), clock);

        PayoutResult accepted = adapter.pay(COMMAND);
        assertEquals(false, accepted.accepted(), "回调未到，本端不应认为已出款");
        assertEquals(QueryState.UNKNOWN, adapter.query(PAYOUT_NO).state(), "等回执 = 未知");

        clock.advance(Duration.ofMinutes(6));
        assertEquals(QueryState.SUCCESS, adapter.query(PAYOUT_NO).state(), "回执到点后收敛为成功");
    }

    private static MockPayoutAdapter mock(MockPayoutAdapter.Mode mode, Duration delay, Clock clock) {
        return new MockPayoutAdapter(mode, delay, clock, Map.of("app-id", "wx-app", "mch-id", "mch-1",
                "api-key", "k"));
    }

    /** 可控时钟：让异步回执的时间点可被精确推进（不用 sleep）。 */
    private static final class MutableClock extends Clock {
        private Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
