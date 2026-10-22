package org.icedamericanomall.trade.infrastructure.adapter.payout;

import org.icedamericanomall.trade.domain.port.WithdrawalPayoutPort.PayoutCommand;
import org.icedamericanomall.trade.domain.port.WithdrawalPayoutPort.PayoutResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * `terminal` 语义契约：**accepted 与 terminal 是两个独立维度**。
 *
 * <p>编译器只检查构造点，不检查断言 —— 所以这里把五处构造点的 terminal 逐个钉住。
 * 反例：若 Manual 报 terminal=false，人工渠道的终态会被当成"受理未到账"，
 * 单据永远卡在 REQUESTED，只能靠人工翻库。
 */
@DisplayName("出款结果 terminal 语义契约")
class PayoutResultTerminalContractTest {

    private static final PayoutCommand COMMAND =
            new PayoutCommand("9f3ac1b27e0d4a56-1", 1L, 2L, 10_000, "6222", "招商银行");

    @Test
    @DisplayName("人工渠道：accepted=true 且 terminal=true（钱已在系统外转出）")
    void manualShouldBeAcceptedAndTerminal() {
        PayoutResult result = new ManualPayoutAdapter().pay(COMMAND);

        assertTrue(result.accepted());
        assertTrue(result.terminal(), "人工确认是终态；报 false 会让单据永远停在 REQUESTED");
    }

    @Test
    @DisplayName("mock 受理：accepted=true 但 terminal=false（受理 ≠ 到账）")
    void acceptedMockShouldNotBeTerminal() {
        PayoutResult result = mock(MockPayoutAdapter.Mode.ACCEPTED).pay(COMMAND);

        assertTrue(result.accepted());
        assertFalse(result.terminal(), "受理不是到账，后续仍要由 query 收敛");
    }

    @Test
    @DisplayName("mock 明确拒绝：accepted=false 且 terminal=true（终态失败，可换号重发）")
    void rejectedMockShouldBeTerminal() {
        PayoutResult result = mock(MockPayoutAdapter.Mode.REJECTED).pay(COMMAND);

        assertFalse(result.accepted());
        assertTrue(result.terminal(), "明确拒绝是终态，否则会被当成「未知」永远只查不重发");
    }

    @Test
    @DisplayName("mock 超时：accepted=false 且 terminal=false（未知，只查不重发）")
    void timeoutMockShouldNotBeTerminal() {
        PayoutResult result = mock(MockPayoutAdapter.Mode.TIMEOUT).pay(COMMAND);

        assertFalse(result.accepted());
        assertFalse(result.terminal(), "超时是未知：既不能判失败，也不能当终态");
    }

    private static MockPayoutAdapter mock(MockPayoutAdapter.Mode mode) {
        return new MockPayoutAdapter(mode, Duration.ZERO, Clock.systemUTC(),
                Map.of("app-id", "wx-app", "mch-id", "mch-1", "api-key", "k"));
    }
}
