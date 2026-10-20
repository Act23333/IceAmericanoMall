package org.icedamericanomall.trade.domain.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 哨兵往返：编码/解码必须**互为反函数**，且真实值撞哨兵时写入要拒绝。
 *
 * <p>这一层单独测，是因为它一旦泄漏，报表里会出现"看起来是数据、其实是占位"的假值 ——
 * 这类错在差异统计里极难发现。
 */
@DisplayName("ReconciliationSentinels 对账 NULL 哨兵")
class ReconciliationSentinelsTest {

    @Test
    @DisplayName("NULL ⇒ 哨兵 ⇒ 还原回 NULL（往返）")
    void shouldRoundTripNull() {
        assertEquals("__NULL__", ReconciliationSentinels.encode(null));
        assertNull(ReconciliationSentinels.decode(ReconciliationSentinels.encode(null)));
    }

    @Test
    @DisplayName("普通值往返不变（含中文、短横、长值）")
    void shouldRoundTripRealValues() {
        for (String value : new String[] {"WD-1", "渠道-流水-001", "x".repeat(150)}) {
            assertEquals(value, ReconciliationSentinels.decode(ReconciliationSentinels.encode(value)));
        }
    }

    @Test
    @DisplayName("真实值恰好等于哨兵 ⇒ 写入拒绝（否则会被误还原成 NULL）")
    void shouldRejectValueEqualToSentinel() {
        assertThrows(IllegalArgumentException.class,
                () -> ReconciliationSentinels.encode(ReconciliationSentinels.NULL_SENTINEL));
    }

    @Test
    @DisplayName("解码只认完整哨兵，形近值不受影响")
    void shouldNotDecodeLookAlikes() {
        assertEquals("__NULL__x", ReconciliationSentinels.decode("__NULL__x"));
        assertEquals("_NULL_", ReconciliationSentinels.decode("_NULL_"));
    }
}
