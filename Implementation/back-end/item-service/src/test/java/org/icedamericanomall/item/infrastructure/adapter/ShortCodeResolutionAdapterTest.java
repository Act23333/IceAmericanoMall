package org.icedamericanomall.item.infrastructure.adapter;

import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.item.domain.enums.ShortCodeHitSource;
import org.icedamericanomall.item.domain.model.spu.Spu;
import org.icedamericanomall.item.domain.repository.SpuRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 短码三级缓存解析测试（ADR-063 §决策 8，批次 B7b）—— 逐条锁死<b>五条硬断言</b>与三道防线。
 *
 * <ul>
 *   <li>① <b>清空 L1+L2 后仍能解析</b>（DB 是唯一真相源）—— {@link #shouldResolveFromDb_whenBothCachesEmpty()}；</li>
 *   <li>② <b>Redis 挂了不是 404</b>（fail-open 到下一层）—— {@link #shouldFallBackToDb_whenRedisThrows()}；</li>
 *   <li>③ 防穿透：不存在 → 负缓存，重复请求只打一次库；</li>
 *   <li>④ 防击穿：同一短码并发重建只查一次库（single-flight）；</li>
 *   <li>⑤ L1 → L2 → L3 逐层降级，命中计数可观测。</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ShortCodeResolutionAdapter —— 三级缓存 / 负缓存 / single-flight / Redis fail-open")
class ShortCodeResolutionAdapterTest {

    private static final String SHORT_CODE = "8f3a1c9d20";
    private static final String SPU_ID = "0f1e2d3c4b5a69788796a5b4c3d2e1f0";
    private static final String REDIS_KEY = ShortCodeResolutionAdapter.REDIS_KEY_PREFIX + SHORT_CODE;

    @Mock
    private SpuRepository spuRepository;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private ShortCodeResolutionAdapter resolver;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        resolver = new ShortCodeResolutionAdapter(spuRepository, redisTemplate);
    }

    private static Spu spu(String spuId) {
        return Spu.builder().id(1L).spuId(spuId).shortCode(SHORT_CODE).status(1).build();
    }

    // ==================== 五条硬断言 ====================

    @Test
    @DisplayName("硬断言①：清空 L1+L2（Redis 未命中）→ 仍能从 DB 解析成功（DB 是唯一真相源）")
    void shouldResolveFromDb_whenBothCachesEmpty() {
        when(valueOperations.get(REDIS_KEY)).thenReturn(null);
        when(spuRepository.findByShortCode(SHORT_CODE)).thenReturn(spu(SPU_ID));

        assertEquals(SPU_ID, resolver.resolve(SHORT_CODE));
        assertEquals(1, resolver.hitCount(ShortCodeHitSource.DB));
        // 回填两级缓存（L2 供跨实例、L1 供本实例下次直接命中）
        verify(valueOperations).set(eq(REDIS_KEY), eq(SPU_ID), eq(Duration.ofDays(7)));
        assertEquals(SPU_ID, resolver.resolve(SHORT_CODE));
        assertEquals(1, resolver.hitCount(ShortCodeHitSource.LOCAL));
        assertEquals(1, resolver.hitCount(ShortCodeHitSource.DB), "第二次必须命中 L1，不再打库");
    }

    @Test
    @DisplayName("硬断言②：Redis 不可用（抛连接异常）→ 回落 DB 解析成功，绝不当作「短码不存在」")
    void shouldFallBackToDb_whenRedisThrows() {
        when(valueOperations.get(anyString()))
                .thenThrow(new RedisConnectionFailureException("Redis down"));
        when(spuRepository.findByShortCode(SHORT_CODE)).thenReturn(spu(SPU_ID));

        String resolved = resolver.resolve(SHORT_CODE);

        assertNotNull(resolved, "Redis 挂了必须 fail-open 到下一层，而不是返回 404 语义");
        assertEquals(SPU_ID, resolved);
        assertEquals(1, resolver.hitCount(ShortCodeHitSource.DB));
        assertEquals(0, resolver.hitCount(ShortCodeHitSource.NEGATIVE));
    }

    @Test
    @DisplayName("硬断言②续：Redis 写回填也失败 → 解析结果不受影响（只丢缓存，不丢结果）")
    void shouldStillResolve_whenRedisWriteFails() {
        when(valueOperations.get(anyString())).thenReturn(null);
        org.mockito.Mockito.doThrow(new RedisConnectionFailureException("Redis down"))
                .when(valueOperations).set(anyString(), anyString(), org.mockito.ArgumentMatchers.any(Duration.class));
        when(spuRepository.findByShortCode(SHORT_CODE)).thenReturn(spu(SPU_ID));

        assertEquals(SPU_ID, resolver.resolve(SHORT_CODE));
    }

    @Test
    @DisplayName("硬断言③：短码不存在 → null + 负缓存（重复请求不再打库，30–60s 窗口）")
    void shouldNegativeCache_whenShortCodeUnknown() {
        when(valueOperations.get(REDIS_KEY)).thenReturn(null);
        when(spuRepository.findByShortCode(SHORT_CODE)).thenReturn(null);

        assertNull(resolver.resolve(SHORT_CODE));
        assertNull(resolver.resolve(SHORT_CODE));

        verify(spuRepository, times(1)).findByShortCode(SHORT_CODE);
        assertEquals(2, resolver.hitCount(ShortCodeHitSource.NEGATIVE),
                "首次判定 + 一次负缓存命中，都计入 NEGATIVE（四类计数互斥）");
        assertEquals(0, resolver.hitCount(ShortCodeHitSource.DB),
                "负结果不是「DB 命中」——否则命中率会掩盖真实打库次数");
        verify(valueOperations).set(eq(REDIS_KEY), eq(ShortCodeResolutionAdapter.NEGATIVE_MARK),
                eq(Duration.ofSeconds(45)));
    }

    @Test
    @DisplayName("L2 命中：本地空、Redis 有值 → 直返业务键，零 DB 往返")
    void shouldHitRedis_whenLocalEmpty() {
        when(valueOperations.get(REDIS_KEY)).thenReturn(SPU_ID);

        assertEquals(SPU_ID, resolver.resolve(SHORT_CODE));
        assertEquals(1, resolver.hitCount(ShortCodeHitSource.REDIS));
        verify(spuRepository, never()).findByShortCode(anyString());
        // 第二次走本地
        assertEquals(SPU_ID, resolver.resolve(SHORT_CODE));
        assertEquals(1, resolver.hitCount(ShortCodeHitSource.LOCAL));
    }

    @Test
    @DisplayName("L2 负缓存命中：Redis 标记「不存在」→ 直接 null，不打库")
    void shouldHonourRemoteNegativeMark() {
        when(valueOperations.get(REDIS_KEY)).thenReturn(ShortCodeResolutionAdapter.NEGATIVE_MARK);

        assertNull(resolver.resolve(SHORT_CODE));
        verify(spuRepository, never()).findByShortCode(anyString());
        assertEquals(1, resolver.hitCount(ShortCodeHitSource.NEGATIVE));
    }

    // ==================== 防线与失败语义 ====================

    @Test
    @DisplayName("防击穿（single-flight）：同一短码并发重建 → 只查一次库")
    void shouldLoadOnce_whenConcurrentMisses() throws Exception {
        when(valueOperations.get(REDIS_KEY)).thenReturn(null);
        AtomicInteger dbCalls = new AtomicInteger();
        when(spuRepository.findByShortCode(SHORT_CODE)).thenAnswer(invocation -> {
            dbCalls.incrementAndGet();
            Thread.sleep(30);                       // 放大重建窗口，让并发线程都撞在加载中
            return spu(SPU_ID);
        });

        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<java.util.concurrent.Future<String>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return resolver.resolve(SHORT_CODE);
                }));
            }
            start.countDown();
            for (var future : futures) {
                assertEquals(SPU_ID, future.get(5, TimeUnit.SECONDS));
            }
        } finally {
            pool.shutdownNow();
        }
        assertEquals(1, dbCalls.get(), "Caffeine 的 per-key 串行化必须把并发重建收敛成一次加载");
    }

    @Test
    @DisplayName("形态非法：直接 null，不触达 Redis 与 DB（适配层已 400，这里是内部防御）")
    void shouldRejectMalformedWithoutIo() {
        assertNull(resolver.resolve("XYZ"));
        assertNull(resolver.resolve(null));
        verifyNoInteractions(valueOperations, spuRepository);
    }

    @Test
    @DisplayName("DB 故障 → INTERNAL_ERROR(500)，不伪装成 404、不写负缓存")
    void shouldFailLoudWhenDbDown() {
        when(valueOperations.get(REDIS_KEY)).thenReturn(null);
        when(spuRepository.findByShortCode(SHORT_CODE)).thenThrow(new RuntimeException("db down"));

        BizException ex = assertThrows(BizException.class, () -> resolver.resolve(SHORT_CODE));

        assertEquals(ErrorCode.INTERNAL_ERROR, ex.getErrorCode());
        assertEquals(500, ErrorCode.INTERNAL_ERROR.getHttpStatus().value());
        // 关键：绝不能把一次抖动缓存成「不存在」（45s 内会把好码判死）
        verify(valueOperations, never()).set(eq(REDIS_KEY),
                eq(ShortCodeResolutionAdapter.NEGATIVE_MARK), org.mockito.ArgumentMatchers.any(Duration.class));
        // 再请求一次仍会重试真相源（不残留错误结论）
        assertThrows(BizException.class, () -> resolver.resolve(SHORT_CODE));
        assertEquals(0, resolver.hitCount(ShortCodeHitSource.NEGATIVE));
    }

    @Test
    @DisplayName("四类命中计数互斥且可观测（LOCAL/REDIS/DB/NEGATIVE）")
    void shouldCountFourSources() {
        when(valueOperations.get(anyString())).thenReturn(null);
        when(spuRepository.findByShortCode(SHORT_CODE)).thenReturn(spu(SPU_ID));

        resolver.resolve(SHORT_CODE);              // DB
        resolver.resolve(SHORT_CODE);              // LOCAL
        resolver.resolve("0000000000");            // DB（另一个码，未 stub → null）
        resolver.resolve("0000000000");            // NEGATIVE（负缓存）

        assertEquals(1, resolver.hitCount(ShortCodeHitSource.DB));
        assertEquals(1, resolver.hitCount(ShortCodeHitSource.LOCAL));
        assertEquals(2, resolver.hitCount(ShortCodeHitSource.NEGATIVE));
        assertEquals(0, resolver.hitCount(ShortCodeHitSource.REDIS));
    }
}
