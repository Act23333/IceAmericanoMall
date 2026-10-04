package org.icedamericanomall.fallback;

import org.icedamericanomall.client.AfterSaleClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * 售后回写降级语义测试（ADR-011 三分法 —— <b>fail-open + 告警</b>）。
 *
 * <p>{@code markRefunded} 是退款成功后的下游状态回写：此处 fail-closed 会把一次
 * <b>已成功</b>的退款在 pay-service 侧判为失败（钱已退、状态被篡改），故必须 fail-open。
 * 本测试锁死「服务不可用时不抛异常」。
 */
@DisplayName("AfterSaleClientFallback — 状态回写 fail-open")
class AfterSaleClientFallbackTest {

    private final AfterSaleClient client = new AfterSaleClientFallback()
            .create(new RuntimeException("trade-service unavailable"));

    @Test
    @DisplayName("服务不可用 → 静默返回（不抛异常），由对账/重试收敛")
    void shouldNotThrow_whenTradeServiceDown() {
        assertDoesNotThrow(() -> client.markRefunded(1L));
    }
}
