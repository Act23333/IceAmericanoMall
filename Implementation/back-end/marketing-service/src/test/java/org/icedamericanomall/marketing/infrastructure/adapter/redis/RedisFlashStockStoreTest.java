package org.icedamericanomall.marketing.infrastructure.adapter.redis;

import org.icedamericanomall.marketing.domain.port.FlashStockStore;
import org.icedamericanomall.marketing.infrastructure.constant.MarketingRedisKeys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RedisFlashStockStore 单元测试 —— 库存预热分摊、惰性兜底初始化、键布局与返回值语义。
 *
 * <p>Lua 调用形参约定：预扣 {@code execute(script, keys, limit, ttl)}（2 个 ARGV），
 * 惰性初始化 {@code execute(script, keys, perShard, remainder, ttl)}（3 个 ARGV）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("RedisFlashStockStore 库存分片与惰性兜底单元测试")
class RedisFlashStockStoreTest {

    private static final Long FLASH_ID = 1L;
    private static final Long USER_ID = 7L;
    private static final int SHARDS = 10;
    private static final long EXPECTED_TTL_SECONDS = MarketingRedisKeys.DEFAULT_TTL.getSeconds();

    @Mock RedisTemplate<String, String> redisTemplate;
    @Mock ValueOperations<String, String> valueOperations;

    private RedisFlashStockStore store;

    @BeforeEach
    void setUp() {
        store = new RedisFlashStockStore(redisTemplate);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    @DisplayName("preloadStock — 库存均分到 10 个分片并写初始化哨兵")
    void shouldDistributeStockAndSetSentinel_whenStockDivisible() {
        store.preloadStock(FLASH_ID, 100, Duration.ofDays(7));

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations, times(SHARDS + 1))
                .set(keyCaptor.capture(), valueCaptor.capture(), eq(Duration.ofDays(7)));

        List<String> keys = keyCaptor.getAllValues();
        List<String> values = valueCaptor.getAllValues();
        for (int i = 0; i < SHARDS; i++) {
            assertEquals("flash:stock:1:" + i, keys.get(i));
            assertEquals("10", values.get(i));
        }
        assertEquals("flash:stock-init:1", keys.get(SHARDS));
        assertEquals("1", values.get(SHARDS));
    }

    @Test
    @DisplayName("preloadStock — 除不尽的库存：前 remainder 片各多 1")
    void shouldSpreadRemainder_whenStockNotDivisible() {
        store.preloadStock(FLASH_ID, 105, Duration.ofDays(7));

        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations, times(SHARDS + 1))
                .set(any(), valueCaptor.capture(), eq(Duration.ofDays(7)));

        List<String> values = valueCaptor.getAllValues();
        for (int i = 0; i < 5; i++) {
            assertEquals("11", values.get(i), "第 " + i + " 片应为 11");
        }
        for (int i = 5; i < SHARDS; i++) {
            assertEquals("10", values.get(i), "第 " + i + " 片应为 10");
        }
    }

    @Test
    @DisplayName("getTotalStock — 汇总各分片剩余库存")
    void shouldSumShardStocks_whenGetTotalStock() {
        for (int i = 0; i < SHARDS; i++) {
            when(valueOperations.get("flash:stock:1:" + i)).thenReturn(String.valueOf(i));
        }

        assertEquals(45L, store.getTotalStock(FLASH_ID));
    }

    @Test
    @DisplayName("tryDeduct — 冷启动（Redis 无键）按 DB 剩余库存初始化分片后再预扣")
    void shouldInitShardsThenDeduct_whenRedisCold() {
        stubDeduct(9L);
        stubInit();

        long remaining = store.tryDeduct(FLASH_ID, USER_ID, 1, 100, MarketingRedisKeys.DEFAULT_TTL);

        assertEquals(9L, remaining);
        // 惰性兜底：10 分片 + 哨兵，按 DB 剩余库存 100 均分（每片 10），TTL 透传
        verify(redisTemplate).execute(any(RedisScript.class), eq(expectedInitKeys()),
                eq("10"), eq("0"), eq(String.valueOf(EXPECTED_TTL_SECONDS)));
        // 预扣键：分片库存 key + 用户限购 key（统一常量构造）
        assertTrue(capturedDeductKeyLists().stream()
                        .anyMatch(keys -> keys.contains(MarketingRedisKeys.flashUser(FLASH_ID, USER_ID))),
                "预扣必须使用统一常量构造的用户限购键");
    }

    @Test
    @DisplayName("tryDeduct — DB 剩余库存为 0 时不初始化（直接判定售罄）")
    void shouldSkipInit_whenDbRemainingStockZero() {
        stubDeduct(FlashStockStore.SOLD_OUT);

        long remaining = store.tryDeduct(FLASH_ID, USER_ID, 1, 0, MarketingRedisKeys.DEFAULT_TTL);

        assertEquals(FlashStockStore.SOLD_OUT, remaining);
        // 仅 10 次预扣调用，无初始化调用
        verify(redisTemplate, times(SHARDS)).execute(any(RedisScript.class), anyList(), any(), any());
    }

    @Test
    @DisplayName("tryDeduct — 全部分片售罄 → SOLD_OUT")
    void shouldReturnSoldOut_whenAllShardsExhausted() {
        stubDeduct(FlashStockStore.SOLD_OUT);
        stubInit();

        assertEquals(FlashStockStore.SOLD_OUT,
                store.tryDeduct(FLASH_ID, USER_ID, 1, 100, MarketingRedisKeys.DEFAULT_TTL));
    }

    @Test
    @DisplayName("tryDeduct — 已达限购 → LIMIT_EXCEEDED")
    void shouldReturnLimitExceeded_whenUserReachedLimit() {
        stubDeduct(FlashStockStore.LIMIT_EXCEEDED);
        stubInit();

        assertEquals(FlashStockStore.LIMIT_EXCEEDED,
                store.tryDeduct(FLASH_ID, USER_ID, 1, 100, MarketingRedisKeys.DEFAULT_TTL));
    }

    /** 预扣调用：execute(script, keys, limit, ttl)。 */
    private void stubDeduct(long result) {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(), any())).thenReturn(result);
    }

    /** 惰性初始化调用：execute(script, keys, perShard, remainder, ttl)。 */
    private void stubInit() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(), any(), any())).thenReturn(1L);
    }

    /** 期望的初始化键集合：10 个分片 key（0..9）+ 初始化哨兵 key。 */
    private List<String> expectedInitKeys() {
        List<String> keys = new ArrayList<>(SHARDS + 1);
        for (int i = 0; i < SHARDS; i++) {
            keys.add(MarketingRedisKeys.flashStockShard(FLASH_ID, i));
        }
        keys.add(MarketingRedisKeys.flashStockInit(FLASH_ID));
        return keys;
    }

    /** 捕获预扣调用（2 个 ARGV）传入的 key 列表。 */
    @SuppressWarnings("unchecked")
    private List<List<String>> capturedDeductKeyLists() {
        ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
        verify(redisTemplate, atLeastOnce())
                .execute(any(RedisScript.class), captor.capture(), any(), any());
        return captor.getAllValues();
    }
}
