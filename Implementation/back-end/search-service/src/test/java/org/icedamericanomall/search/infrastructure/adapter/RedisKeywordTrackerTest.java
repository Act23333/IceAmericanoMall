package org.icedamericanomall.search.infrastructure.adapter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * RedisKeywordTracker 前缀联想单元测试 —— SE5 源 1：热度降序保持、前缀（不区分大小写）过滤、
 * Redis 异常降级空列表（端口契约：联想故障不得影响主流程）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RedisKeywordTrackerTest {

    private static final String HOT_KEY = "search:hot:keywords";

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ZSetOperations<String, String> zSetOperations;

    private RedisKeywordTracker tracker;

    @BeforeEach
    void setUp() {
        tracker = new RedisKeywordTracker(redisTemplate);
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
    }

    /** 模拟 ZSet reverseRange 返回（LinkedHashSet 保持热度降序，与 Redis 真实行为一致）。 */
    private void stubTop(Set<String> top) {
        when(zSetOperations.reverseRange(eq(HOT_KEY), eq(0L), anyLong())).thenReturn(top);
    }

    @Test
    @DisplayName("前缀联想 — 只保留前缀命中，保持热度降序")
    void shouldFilterByPrefixKeepingHotOrder_whenHotKeywordsMatch() {
        stubTop(new LinkedHashSet<>(List.of("手机壳", "手机", "蓝牙耳机", "耳机套")));

        List<String> hits = tracker.getHotKeywordsByPrefix("手机", 10);

        assertEquals(List.of("手机壳", "手机"), hits);
    }

    @Test
    @DisplayName("前缀联想 — 不区分大小写（与商品名源 MySQL ci collation 口径对齐）")
    void shouldMatchCaseInsensitive_whenPrefixCaseDiffers() {
        stubTop(new LinkedHashSet<>(List.of("iPhone 15", "蓝牙", "IPHONE CASE")));

        List<String> hits = tracker.getHotKeywordsByPrefix("iphone", 10);

        assertEquals(List.of("iPhone 15", "IPHONE CASE"), hits);
    }

    @Test
    @DisplayName("前缀联想 — limit 截断生效")
    void shouldLimitResults_whenLimitSmallerThanHits() {
        stubTop(new LinkedHashSet<>(List.of("手机壳", "手机", "手机膜")));

        assertEquals(List.of("手机壳"), tracker.getHotKeywordsByPrefix("手机", 1));
    }

    @Test
    @DisplayName("前缀联想 — 热搜榜为空 → 空列表")
    void shouldReturnEmpty_whenHotKeywordsEmpty() {
        stubTop(new LinkedHashSet<>());

        assertTrue(tracker.getHotKeywordsByPrefix("手机", 10).isEmpty());
    }

    @Test
    @DisplayName("前缀联想 — Redis 异常 → 空列表不抛出（端口容错契约）")
    void shouldReturnEmpty_whenRedisFails() {
        when(zSetOperations.reverseRange(anyString(), anyLong(), anyLong()))
                .thenThrow(new RuntimeException("redis down"));

        assertTrue(tracker.getHotKeywordsByPrefix("手机", 10).isEmpty());
    }
}
