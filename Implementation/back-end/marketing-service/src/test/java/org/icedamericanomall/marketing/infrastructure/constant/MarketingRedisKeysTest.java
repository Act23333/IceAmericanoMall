package org.icedamericanomall.marketing.infrastructure.constant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MarketingRedisKeys 单元测试 —— 键布局与 TTL 对齐规则（与活动结束时间对齐，兜底 7 天）。
 */
@DisplayName("MarketingRedisKeys 键布局与 TTL 单元测试")
class MarketingRedisKeysTest {

    @Test
    @DisplayName("键布局 — 秒杀/优惠券/签到键前缀与拼接")
    void shouldBuildExpectedKeys() {
        assertEquals("flash:stock:1:3", MarketingRedisKeys.flashStockShard(1L, 3));
        assertEquals("flash:stock-init:1", MarketingRedisKeys.flashStockInit(1L));
        assertEquals("flash:user:1:7", MarketingRedisKeys.flashUser(1L, 7L));
        assertEquals("coupon:grab:stock:9", MarketingRedisKeys.couponGrabStock(9L));
        assertEquals("coupon:grab:claimed:9", MarketingRedisKeys.couponGrabClaimed(9L));
        assertEquals("coupon:claimed:9", MarketingRedisKeys.couponClaimed(9L));
        assertEquals("user:sign:7:2026:10", MarketingRedisKeys.userSign(7L, "2026:10"));
    }

    @Test
    @DisplayName("ttlUntil — 结束时间缺失/已过期 → 兜底 7 天")
    void shouldFallbackToDefaultTtl_whenEndTimeMissingOrPast() {
        assertEquals(MarketingRedisKeys.DEFAULT_TTL, MarketingRedisKeys.ttlUntil(null));
        assertEquals(MarketingRedisKeys.DEFAULT_TTL,
                MarketingRedisKeys.ttlUntil(LocalDateTime.now().minusDays(1)));
    }

    @Test
    @DisplayName("ttlUntil — 结束时间在远期 → TTL 大于 7 天兜底值")
    void shouldExtendBeyondDefaultTtl_whenEndTimeFarAhead() {
        assertTrue(MarketingRedisKeys.ttlUntil(LocalDateTime.now().plusDays(30))
                .compareTo(MarketingRedisKeys.DEFAULT_TTL) > 0);
    }
}
