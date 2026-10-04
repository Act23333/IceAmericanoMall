package org.icedamericanomall.auth.infrastructure.adapter;

import org.icedamericanomall.auth.app.port.TokenVersionService;
import org.icedamericanomall.auth.infrastructure.constant.RedisKeyConstants;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 用户级令牌版本实现单元测试（ADR-031）。
 *
 * <p>覆盖契约键形状（{@code auth:tokenver:{userId}}）、「键缺失 = 版本 0」兼容规则、
 * {@code INCR} 推进语义、userId 缺失跳过，以及 Redis 异常时 fail-open（不抛异常、返回可识别哨兵）。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TokenVersionServiceImpl 单元测试")
class TokenVersionServiceImplTest {

    private static final Long USER_ID = 100L;
    private static final String VERSION_KEY = RedisKeyConstants.JWT_TOKEN_VERSION_PREFIX + USER_ID;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private TokenVersionServiceImpl tokenVersionService;

    // ========== currentVersion ==========

    @Test
    @DisplayName("读取 — 键缺失（从未被吊销）→ 版本 0（兼容规则：存量 token 无 ver claim 亦按 0）")
    void shouldReturnZero_whenVersionKeyMissing() {
        stubRedis();
        when(valueOperations.get(VERSION_KEY)).thenReturn(null);

        assertEquals(0L, tokenVersionService.currentVersion(USER_ID));
    }

    @Test
    @DisplayName("读取 — 键存在 → 返回其十进制版本值")
    void shouldReturnStoredVersion_whenVersionKeyPresent() {
        stubRedis();
        when(valueOperations.get(VERSION_KEY)).thenReturn("3");

        assertEquals(3L, tokenVersionService.currentVersion(USER_ID));
    }

    @Test
    @DisplayName("读取 — 键为空白 → 按版本 0 处理")
    void shouldReturnZero_whenVersionValueBlank() {
        stubRedis();
        when(valueOperations.get(VERSION_KEY)).thenReturn("  ");

        assertEquals(0L, tokenVersionService.currentVersion(USER_ID));
    }

    @Test
    @DisplayName("读取 — 值不可解析 → 按版本 0 处理（不抛异常）")
    void shouldReturnZero_whenVersionValueUnparseable() {
        stubRedis();
        when(valueOperations.get(VERSION_KEY)).thenReturn("not-a-number");

        assertEquals(0L, tokenVersionService.currentVersion(USER_ID));
    }

    @Test
    @DisplayName("读取 — Redis 异常 → fail-open 返回 0，不阻断签发链路")
    void shouldReturnZero_whenRedisFailsOnRead() {
        stubRedis();
        when(valueOperations.get(VERSION_KEY)).thenThrow(new IllegalStateException("redis down"));

        assertEquals(0L, tokenVersionService.currentVersion(USER_ID));
    }

    @Test
    @DisplayName("读取 — userId 缺失 → 返回 0 且不访问 Redis")
    void shouldSkipRead_whenUserIdMissing() {
        assertEquals(0L, tokenVersionService.currentVersion(null));

        verifyNoInteractions(stringRedisTemplate);
    }

    // ========== incrementVersion ==========

    @Test
    @DisplayName("推进 — 按契约键 INCR → 返回新版本（改密/禁用即全端下线）")
    void shouldIncrementVersion_whenCalled() {
        stubRedis();
        when(valueOperations.increment(VERSION_KEY)).thenReturn(1L);

        assertEquals(1L, tokenVersionService.incrementVersion(USER_ID));

        verify(valueOperations).increment(VERSION_KEY);
    }

    @Test
    @DisplayName("推进 — userId 缺失 → 返回失败哨兵且不访问 Redis")
    void shouldSkipIncrement_whenUserIdMissing() {
        assertEquals(TokenVersionService.INCREMENT_FAILED, tokenVersionService.incrementVersion(null));

        verifyNoInteractions(stringRedisTemplate);
    }

    @Test
    @DisplayName("fail-open — Redis 推进异常 → 不抛出，返回失败哨兵（改密/禁用主流程不被阻断）")
    void shouldReturnFailedSentinel_whenRedisFailsOnIncrement() {
        stubRedis();
        when(valueOperations.increment(VERSION_KEY)).thenThrow(new IllegalStateException("redis down"));

        assertDoesNotThrow(() -> assertEquals(TokenVersionService.INCREMENT_FAILED,
                tokenVersionService.incrementVersion(USER_ID)));
    }

    @Test
    @DisplayName("fail-open — INCR 返回空值 → 返回失败哨兵，不抛 NPE")
    void shouldReturnFailedSentinel_whenIncrementReturnsNull() {
        stubRedis();
        when(valueOperations.increment(VERSION_KEY)).thenReturn(null);

        assertEquals(TokenVersionService.INCREMENT_FAILED, tokenVersionService.incrementVersion(USER_ID));
    }

    @Test
    @DisplayName("写侧 — 只做 INCR，绝不设置 TTL（过期会把已吊销令牌复活）")
    void shouldNeverExpireVersionKey() {
        stubRedis();
        when(valueOperations.increment(VERSION_KEY)).thenReturn(2L);

        tokenVersionService.incrementVersion(USER_ID);

        verify(valueOperations, never()).set(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("契约键 — 前缀与网关 JwtBlacklistFilter 读取的键完全一致（auth:tokenver:）")
    void shouldUseGatewayContractKeyPrefix() {
        assertEquals("auth:tokenver:", RedisKeyConstants.JWT_TOKEN_VERSION_PREFIX);
    }

    private void stubRedis() {
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
    }
}
