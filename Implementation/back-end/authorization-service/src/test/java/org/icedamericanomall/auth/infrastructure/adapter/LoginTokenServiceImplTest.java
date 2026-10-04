package org.icedamericanomall.auth.infrastructure.adapter;

import org.icedamericanomall.auth.app.dto.TokenGenerateParam;
import org.icedamericanomall.auth.app.dto.TokenPair;
import org.icedamericanomall.auth.app.port.TokenVersionService;
import org.icedamericanomall.auth.domain.enums.PlatformEnum;
import org.icedamericanomall.auth.domain.model.RefreshTokenInfo;
import org.icedamericanomall.auth.infrastructure.config.JwtProperties;
import org.icedamericanomall.auth.infrastructure.utils.JwtUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 登录令牌服务单元测试（ADR-031：access token 携带 {@code ver} claim + 用户级会话拆除）。
 *
 * <p>覆盖：登录签发与 refresh 换发均写入当前令牌版本；用户级拆会话按端类型遍历删除
 * （会话键 + 反查索引），无会话 / userId 缺失时安全跳过。
 *
 * <p>实例以显式构造装配（而非 {@code @InjectMocks}）：生产类同时依赖
 * {@link StringRedisTemplate} 与 {@link RedisTemplate}（前者是其子类），
 * 由 Mockito 按类型自动注入存在歧义风险，显式构造可保证两个模板桩落在正确的字段上。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("LoginTokenServiceImpl 单元测试")
class LoginTokenServiceImplTest {

    private static final Long USER_ID = 100L;
    private static final String ACCESS_TOKEN = "access-token-1";
    private static final long ACCESS_TTL_SECONDS = 1800L;
    private static final long REFRESH_TTL_SECONDS = 604800L;
    private static final String REFRESH_TOKEN = "r1";
    private static final String DEVICE_TYPE_WEB = "WEB";
    private static final String SESSION_KEY_WEB = "login:user:refresh:" + USER_ID + ":" + DEVICE_TYPE_WEB;
    private static final String INDEX_KEY = "login:user:token:key:" + REFRESH_TOKEN;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private JwtUtils jwtUtils;

    @Mock
    private JwtProperties jwtProperties;

    @Mock
    private TokenVersionService tokenVersionService;

    @Mock
    private ValueOperations<String, String> stringValueOperations;

    @Mock
    private ValueOperations<String, Object> objectValueOperations;

    private LoginTokenServiceImpl loginTokenService;

    @BeforeEach
    void setUp() {
        loginTokenService = new LoginTokenServiceImpl(
                stringRedisTemplate, redisTemplate, jwtUtils, jwtProperties, tokenVersionService);
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(stringValueOperations);
        lenient().when(redisTemplate.opsForValue()).thenReturn(objectValueOperations);
        // 默认：绝大多数端没有会话（键缺失）
        lenient().when(objectValueOperations.get(anyString())).thenReturn(null);
    }

    // ========== ver claim（签发链路） ==========

    @Test
    @DisplayName("登录签发 — access token 写入当前令牌版本 claim（ver = auth:tokenver:{userId}）")
    void shouldSignTokenWithCurrentVersion_whenLogin() {
        stubIssueChain();
        when(tokenVersionService.currentVersion(USER_ID)).thenReturn(5L);

        TokenPair tokenPair = loginTokenService.createLoginResponse(param());

        assertEquals(ACCESS_TOKEN, tokenPair.accessToken());
        assertEquals(5L, capturedClaims().get(JwtUtils.TOKEN_VERSION_CLAIM),
                "签发时刻的用户令牌版本必须写入 ver claim");
    }

    @Test
    @DisplayName("刷新换发 — 新 access token 同样携带当前令牌版本（改密后版本已推进则刷新亦不可绕过）")
    void shouldSignTokenWithCurrentVersion_whenRefresh() {
        stubIssueChain();
        when(tokenVersionService.currentVersion(USER_ID)).thenReturn(7L);
        when(stringValueOperations.get(INDEX_KEY)).thenReturn(SESSION_KEY_WEB);
        when(objectValueOperations.get(SESSION_KEY_WEB)).thenReturn(session());

        loginTokenService.createRefreshResponse(REFRESH_TOKEN);

        assertEquals(7L, capturedClaims().get(JwtUtils.TOKEN_VERSION_CLAIM),
                "refresh 换发必须使用当前版本，不得继承旧版本");
    }

    @Test
    @DisplayName("兼容 — 键缺失（currentVersion=0）→ ver claim 写 0，与无 claim 的存量令牌同义")
    void shouldSignZeroVersion_whenVersionKeyMissing() {
        stubIssueChain();
        when(tokenVersionService.currentVersion(USER_ID)).thenReturn(0L);

        loginTokenService.createLoginResponse(param());

        assertEquals(0L, capturedClaims().get(JwtUtils.TOKEN_VERSION_CLAIM));
    }

    // ========== revokeAllSessions（用户级拆除） ==========

    @Test
    @DisplayName("用户级拆会话 — 存在的会话被删除（会话键 + 反查索引），未命中端不产生删除")
    void shouldDeleteExistingSessionsAcrossAllDeviceTypes_whenRevokeAll() {
        when(objectValueOperations.get(SESSION_KEY_WEB)).thenReturn(session());
        when(stringValueOperations.get(INDEX_KEY)).thenReturn(SESSION_KEY_WEB);

        loginTokenService.revokeAllSessions(USER_ID);

        verify(redisTemplate).delete(SESSION_KEY_WEB);
        verify(stringRedisTemplate).delete(INDEX_KEY);
        verify(redisTemplate, never()).delete("login:user:refresh:" + USER_ID + ":H5");
        verify(redisTemplate, never()).delete("login:user:refresh:" + USER_ID + ":UNKNOWN");
    }

    @Test
    @DisplayName("用户级拆会话 — 该用户无任何会话 → 不产生删除，也不抛异常")
    void shouldDoNothing_whenNoSessionExists() {
        loginTokenService.revokeAllSessions(USER_ID);

        verify(redisTemplate, never()).delete(anyString());
        verify(stringRedisTemplate, never()).delete(anyString());
    }

    @Test
    @DisplayName("用户级拆会话 — userId 缺失 → 跳过且不访问 Redis")
    void shouldSkipRevokeAll_whenUserIdMissing() {
        loginTokenService.revokeAllSessions(null);

        verifyNoInteractions(redisTemplate, stringRedisTemplate);
    }

    // ========== 脚手架 ==========

    /** 桩：签发链路（JWT 生成；会话写入 Redis 为 void 方法，默认即无操作）。 */
    private void stubIssueChain() {
        when(jwtUtils.generateToken(anyMap())).thenReturn(ACCESS_TOKEN);
        when(jwtUtils.getJti(ACCESS_TOKEN)).thenReturn("jti-1");
        when(jwtUtils.getTtl(ACCESS_TOKEN)).thenReturn(ACCESS_TTL_SECONDS);
        when(jwtProperties.getRefreshTokenTtl()).thenReturn(REFRESH_TTL_SECONDS);
    }

    private Map<String, Object> capturedClaims() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(jwtUtils).generateToken(captor.capture());
        Map<String, Object> claims = captor.getValue();
        assertNotNull(claims.get("userId"), "签发链路必须带 userId claim");
        return claims;
    }

    private TokenGenerateParam param() {
        return TokenGenerateParam.builder()
                .userId(USER_ID)
                .platform(PlatformEnum.WEB)
                .deviceId("dev-1")
                .extraClaims(Map.of())
                .build();
    }

    private RefreshTokenInfo session() {
        RefreshTokenInfo info = new RefreshTokenInfo();
        info.setRefreshToken(REFRESH_TOKEN);
        info.setAccessTokenJti("jti-1");
        info.setUserId(USER_ID);
        info.setDeviceId("dev-1");
        info.setDeviceType(DEVICE_TYPE_WEB);
        info.setPlatform(PlatformEnum.WEB);
        info.setIssuedAt(System.currentTimeMillis());
        info.setMaxExpireAt(System.currentTimeMillis() + 3_600_000L);
        return info;
    }
}
