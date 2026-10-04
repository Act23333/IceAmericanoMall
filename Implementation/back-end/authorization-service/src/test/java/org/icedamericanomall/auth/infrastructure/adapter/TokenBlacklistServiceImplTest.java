package org.icedamericanomall.auth.infrastructure.adapter;

import org.icedamericanomall.auth.infrastructure.constant.RedisKeyConstants;
import org.icedamericanomall.auth.infrastructure.utils.JwtUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 访问令牌吊销写入实现单元测试（ADR-027）。
 *
 * <p>覆盖契约键形状与 TTL 语义（{@code auth:blacklist:{jti}}、TTL = max(剩余有效期, 1s)）、
 * 无令牌 / 无 jti 时跳过（不产生 Redis 交互），以及 Redis 异常时 fail-open（不抛异常）。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TokenBlacklistServiceImpl 单元测试")
class TokenBlacklistServiceImplTest {

    private static final String ACCESS_TOKEN = "header.payload.signature";
    private static final String JTI = "jti-abc123";
    private static final long REMAINING_SECONDS = 120L;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private JwtUtils jwtUtils;

    @InjectMocks
    private TokenBlacklistServiceImpl tokenBlacklistService;

    @Test
    @DisplayName("写入 — 令牌有效期内登出 → 按契约键 auth:blacklist:{jti} 写入，TTL = 剩余有效期")
    void shouldWriteBlacklistKeyWithRemainingTtl_whenTokenHasJti() {
        stubRedis();
        when(jwtUtils.getJti(ACCESS_TOKEN)).thenReturn(JTI);
        when(jwtUtils.getRemainingTtl(ACCESS_TOKEN)).thenReturn(REMAINING_SECONDS);

        tokenBlacklistService.blacklist(ACCESS_TOKEN);

        verify(valueOperations).set(RedisKeyConstants.JWT_BLACKLIST_PREFIX + JTI, "1",
                REMAINING_SECONDS, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("写入 — 剩余有效期取整为 0（临界过期）→ TTL 取 1s 下限，吊销不得被立即清除")
    void shouldUseMinTtl_whenRemainingTtlIsZero() {
        stubRedis();
        when(jwtUtils.getJti(ACCESS_TOKEN)).thenReturn(JTI);
        when(jwtUtils.getRemainingTtl(ACCESS_TOKEN)).thenReturn(0L);

        tokenBlacklistService.blacklist(ACCESS_TOKEN);

        verify(valueOperations).set(RedisKeyConstants.JWT_BLACKLIST_PREFIX + JTI, "1", 1L, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("跳过 — 令牌缺少 jti → 不写黑名单（无键可写，DEBUG 记录）")
    void shouldSkip_whenJtiMissing() {
        when(jwtUtils.getJti(ACCESS_TOKEN)).thenReturn(null);

        tokenBlacklistService.blacklist(ACCESS_TOKEN);

        verifyNoInteractions(stringRedisTemplate);
    }

    @Test
    @DisplayName("跳过 — jti 为空白 → 不写黑名单")
    void shouldSkip_whenJtiBlank() {
        when(jwtUtils.getJti(ACCESS_TOKEN)).thenReturn("  ");

        tokenBlacklistService.blacklist(ACCESS_TOKEN);

        verifyNoInteractions(stringRedisTemplate);
    }

    @Test
    @DisplayName("跳过 — 无 access token（匿名重置密码）→ 不解析令牌、不写黑名单")
    void shouldSkip_whenAccessTokenAbsent() {
        tokenBlacklistService.blacklist(null);
        tokenBlacklistService.blacklist("  ");

        verifyNoInteractions(jwtUtils, stringRedisTemplate);
    }

    @Test
    @DisplayName("fail-open — Redis 写入异常 → 不抛出（登出 / 改密主流程不被阻断）")
    void shouldNotThrow_whenRedisFails() {
        stubRedis();
        when(jwtUtils.getJti(ACCESS_TOKEN)).thenReturn(JTI);
        when(jwtUtils.getRemainingTtl(ACCESS_TOKEN)).thenReturn(REMAINING_SECONDS);
        doThrow(new RuntimeException("redis down")).when(valueOperations)
                .set(anyString(), anyString(), anyLong(), any(TimeUnit.class));

        assertDoesNotThrow(() -> tokenBlacklistService.blacklist(ACCESS_TOKEN));
    }

    private void stubRedis() {
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    @DisplayName("契约键 — 前缀与网关 JwtBlacklistFilter 读取的键完全一致（auth:blacklist:）")
    void shouldUseGatewayContractKeyPrefix() {
        assertEquals("auth:blacklist:", RedisKeyConstants.JWT_BLACKLIST_PREFIX);
    }
}
