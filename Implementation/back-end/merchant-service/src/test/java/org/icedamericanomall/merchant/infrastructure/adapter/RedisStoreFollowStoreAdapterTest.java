package org.icedamericanomall.merchant.infrastructure.adapter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisSetCommands;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 店铺关注 Redis 适配器单元测试 —— 批量取粉丝数的 <b>pipeline 语义</b>。
 *
 * <p>为什么必须锁这一层：{@code ShopVO.follower_count} 回填若退化为「逐店铺 SCARD」，
 * 店铺数量一涨就是 N 次网络往返（N+1）。本测试断言：①一次 {@code executePipelined} 往返；
 * ②下发的键与店铺 ID 一一对应（去重）；③结果按命令顺序映射回店铺（pipeline 保序）；
 * ④空入参不触达 Redis；⑤Redis 异常原样上抛（fail-open 由应用层兜底，本层不吞）。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RedisStoreFollowStoreAdapter — 粉丝数批量读（pipeline）")
class RedisStoreFollowStoreAdapterTest {

    private static final byte[] KEY_SHOP_5 = "shop:followers:5".getBytes(StandardCharsets.UTF_8);
    private static final byte[] KEY_SHOP_6 = "shop:followers:6".getBytes(StandardCharsets.UTF_8);

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private RedisConnection connection;

    @Mock
    private RedisSetCommands setCommands;

    private RedisStoreFollowStoreAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new RedisStoreFollowStoreAdapter(redisTemplate);
    }

    @Test
    @DisplayName("followerCounts — 一次 pipeline 往返：键去重、结果按序映射回店铺")
    void shouldMapCountsInOrder_withSinglePipelineRoundTrip() {
        when(connection.setCommands()).thenReturn(setCommands);
        when(redisTemplate.executePipelined(any(RedisCallback.class))).thenAnswer(invocation -> {
            RedisCallback<?> callback = invocation.getArgument(0);
            callback.doInRedis(connection);
            return List.of(128L, 7L);
        });

        Map<Long, Long> counts = adapter.followerCounts(List.of(5L, 6L, 5L));

        assertEquals(128L, counts.get(5L));
        assertEquals(7L, counts.get(6L));
        assertEquals(2, counts.size(), "重复店铺 ID 去重");
        verify(redisTemplate, times(1)).executePipelined(any(RedisCallback.class));
        verify(setCommands).sCard(KEY_SHOP_5);
        verify(setCommands).sCard(KEY_SHOP_6);
    }

    @Test
    @DisplayName("followerCounts — 键不存在（SCARD 返回 null）→ 记 0，不抛异常")
    void shouldRecordZero_whenKeyMissing() {
        when(connection.setCommands()).thenReturn(setCommands);
        when(redisTemplate.executePipelined(any(RedisCallback.class))).thenAnswer(invocation -> {
            RedisCallback<?> callback = invocation.getArgument(0);
            callback.doInRedis(connection);
            return Arrays.asList(null, 7L);
        });

        Map<Long, Long> counts = adapter.followerCounts(List.of(5L, 6L));

        assertEquals(0L, counts.get(5L), "无人关注 = 0（不是 null：null 保留给 Redis 不可用）");
        assertEquals(7L, counts.get(6L));
    }

    @Test
    @DisplayName("followerCounts — 空集合 / 全 null 入参不触达 Redis")
    void shouldSkipRedis_whenIdsBlank() {
        assertTrue(adapter.followerCounts(List.of()).isEmpty());
        assertTrue(adapter.followerCounts(null).isEmpty());
        assertTrue(adapter.followerCounts(Arrays.asList(null, null)).isEmpty());
        verifyNoInteractions(redisTemplate);
    }

    @Test
    @DisplayName("followerCounts — Redis 异常原样上抛（fail-open 由 ShopManager 兜底）")
    void shouldPropagateRedisFailure() {
        when(redisTemplate.executePipelined(any(RedisCallback.class)))
                .thenThrow(new RedisConnectionFailureException("redis down"));

        assertThrows(RedisConnectionFailureException.class, () -> adapter.followerCounts(List.of(5L)));
    }
}
