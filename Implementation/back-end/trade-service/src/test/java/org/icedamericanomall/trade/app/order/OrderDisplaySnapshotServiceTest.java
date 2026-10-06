package org.icedamericanomall.trade.app.order;

import org.icedamericanomall.client.CurrencyClient;
import org.icedamericanomall.dto.CurrencyRateCO;
import org.icedamericanomall.trade.app.order.strategy.OrderCreateContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * OrderDisplaySnapshotService 单元测试（F16 多币种 Phase 1，契约 §4.14.3b I61，PRD §17.4.3）。
 *
 * <p>Mock {@code CurrencyClient}，验证 fail-open 全分支：未传/CNY 直通不查端点、
 * 降级哨兵 -1 / 查询异常 / 空响应一律跳过快照且不抛异常（订单主流程零影响）、
 * 正常汇率回填 ctx 两字段（随后由 buildBaseOrder 原子落 {@code orders.display_*} 两列）。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OrderDisplaySnapshotService 展示汇率快照（fail-open）")
class OrderDisplaySnapshotServiceTest {

    /** 测试用定点汇率：USD 0.140300 → 140300（×10⁶）。 */
    private static final long USD_RATE_SCALED = 140300L;

    @Mock
    private CurrencyClient currencyClient;

    private OrderDisplaySnapshotService service;

    @BeforeEach
    void setUp() {
        service = new OrderDisplaySnapshotService(currencyClient);
    }

    private OrderCreateContext ctx(String displayCurrency) {
        OrderCreateContext c = new OrderCreateContext();
        c.setUserId(100L);
        c.setDisplayCurrency(displayCurrency);
        return c;
    }

    @Test
    @DisplayName("快照成功 — USD 汇率 140300 → ctx 回填 displayCurrency + displayRate")
    void shouldFillCtxRate_whenValidRateReturned() {
        OrderCreateContext c = ctx("USD");
        when(currencyClient.getCurrent("USD")).thenReturn(new CurrencyRateCO("USD", USD_RATE_SCALED, 2));

        service.snapshotIfNeeded(c);

        assertEquals("USD", c.getDisplayCurrency());
        assertEquals(USD_RATE_SCALED, c.getDisplayRate());
    }

    @Test
    @DisplayName("未传展示币种（null）— 不调汇率端点，ctx 归空（未传=CNY 不快照）")
    void shouldSkipWithoutFeign_whenCurrencyNotProvided() {
        OrderCreateContext c = ctx(null);

        service.snapshotIfNeeded(c);

        assertNull(c.getDisplayCurrency());
        assertNull(c.getDisplayRate());
        verifyNoInteractions(currencyClient);
    }

    @Test
    @DisplayName("CNY 主币种直通 — 不调汇率端点不快照（主币种订单无参考价，PRD §17.4.3 默认态）")
    void shouldSkipWithoutFeign_whenCurrencyIsCNY() {
        OrderCreateContext c = ctx("CNY");

        service.snapshotIfNeeded(c);

        assertNull(c.getDisplayCurrency());
        assertNull(c.getDisplayRate());
        verifyNoInteractions(currencyClient);
    }

    @Test
    @DisplayName("降级哨兵 rateScaled=-1 — 跳过快照且不抛异常（fail-open，PRD §17.7.2）")
    void shouldSkipQuietly_whenDegradedSentinelReturned() {
        OrderCreateContext c = ctx("USD");
        when(currencyClient.getCurrent("USD")).thenReturn(new CurrencyRateCO("USD", -1L, null));

        assertDoesNotThrow(() -> service.snapshotIfNeeded(c));

        assertNull(c.getDisplayCurrency());
        assertNull(c.getDisplayRate());
    }

    @Test
    @DisplayName("汇率查询异常 — 跳过快照且不抛异常（fail-open，绝不阻塞交易主流程）")
    void shouldSkipQuietly_whenClientThrows() {
        OrderCreateContext c = ctx("USD");
        when(currencyClient.getCurrent("USD")).thenThrow(new IllegalStateException("feign down"));

        assertDoesNotThrow(() -> service.snapshotIfNeeded(c));

        assertNull(c.getDisplayCurrency());
        assertNull(c.getDisplayRate());
    }

    @Test
    @DisplayName("空响应（null）— 跳过快照且不抛异常")
    void shouldSkipQuietly_whenClientReturnsNull() {
        OrderCreateContext c = ctx("JPY");
        when(currencyClient.getCurrent("JPY")).thenReturn(null);

        assertDoesNotThrow(() -> service.snapshotIfNeeded(c));

        assertNull(c.getDisplayCurrency());
        assertNull(c.getDisplayRate());
    }
}
