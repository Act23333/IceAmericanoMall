package org.icedamericanomall.trade.app.order;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.util.LinkedHashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * {@link CouponConsumptionLedger} 单测（不依赖真实 Redis）—— 固定键空间与读写口径，
 * 防止键名漂移导致对账任务扫不到标记（那会让兜底静默失效）。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CouponConsumptionLedger — 兜底对账标记读写")
class CouponConsumptionLedgerTest {

    @Mock private StringRedisTemplate stringRedisTemplate;
    @Mock private ZSetOperations<String, String> zSetOperations;

    private CouponConsumptionLedger ledger;

    @BeforeEach
    void setUp() {
        ledger = new CouponConsumptionLedger(stringRedisTemplate);
    }

    @Test
    @DisplayName("markPending — 以时间戳为 score 写入 ZSET（member = 订单号）")
    void shouldAddPendingMarker() {
        when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOperations);

        ledger.markPending("ORD-1");

        verify(zSetOperations).add(eq(CouponConsumptionLedger.PENDING_KEY), eq("ORD-1"), anyDouble());
    }

    @Test
    @DisplayName("markPending — 空/ null 订单号：无幂等锚点，不写库")
    void shouldIgnoreBlankOrderNo_whenMarkPending() {
        ledger.markPending(null);
        ledger.markPending("  ");

        verifyNoInteractions(stringRedisTemplate);
    }

    @Test
    @DisplayName("clear — 按订单号移除标记")
    void shouldRemovePendingMarker() {
        when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOperations);

        ledger.clear("ORD-1");

        verify(zSetOperations).remove(CouponConsumptionLedger.PENDING_KEY, "ORD-1");
    }

    @Test
    @DisplayName("clear — 空订单号：无操作")
    void shouldIgnoreBlankOrderNo_whenClear() {
        ledger.clear(null);

        verifyNoInteractions(stringRedisTemplate);
    }

    @Test
    @DisplayName("listPendingBefore — 按时间上界扫描，返回订单号列表（键空间与窗口参数原样下传）")
    void shouldListPendingBeforeCutoff() {
        when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOperations);
        // 用字面量而非 matcher 打桩：rangeByScore 的 min/max 是 double 形参，
        // eq(1_700_000_000_000L) 装箱为 Long 与实参 Double 不相等（strict stubs 下报 PotentialStubbingProblem）
        when(zSetOperations.rangeByScore(CouponConsumptionLedger.PENDING_KEY, 0d, 1_700_000_000_000d, 0L, 50L))
                .thenReturn(new LinkedHashSet<>(List.of("ORD-1", "ORD-2")));

        List<String> pending = ledger.listPendingBefore(1_700_000_000_000L, 50);

        assertEquals(List.of("ORD-1", "ORD-2"), pending);
        // 窗口必须原样下传：max 退化为 now / 无上界，会把「在途下单」的券一并回收（反向资损）
        verify(zSetOperations).rangeByScore(CouponConsumptionLedger.PENDING_KEY, 0d, 1_700_000_000_000d, 0L, 50L);
    }

    @Test
    @DisplayName("listPendingBefore — Redis 返回 null：归为空列表（调用方零分支）")
    void shouldReturnEmptyList_whenRedisReturnsNull() {
        when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(zSetOperations.rangeByScore(anyString(), anyDouble(), anyDouble(), anyLong(), anyLong()))
                .thenReturn(null);

        assertTrue(ledger.listPendingBefore(1_700_000_000_000L, 50).isEmpty());
    }
}
