package org.icedamericanomall.gateway.filter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * JWT 吊销校验过滤器单元测试（ADR-031：黑名单 + 用户级令牌版本，一次 MGET）。
 *
 * <p>覆盖：黑名单命中 / 版本不一致 → 401 且不分发；版本一致 / 版本键缺失 / ver claim 缺失 → 放行；
 * MGET 异常 → fail-open 放行；缺 userId → 只查黑名单（版本比对跳过）；无 jti / 匿名 → 放行且不查 Redis。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("JwtBlacklistFilter 单元测试")
class JwtBlacklistFilterTest {

    private static final String JTI = "jti-1";
    private static final String USER_ID = "1";
    private static final String BLACKLIST_KEY = "auth:blacklist:" + JTI;
    private static final String VERSION_KEY = "auth:tokenver:" + USER_ID;

    @Mock
    private ReactiveStringRedisTemplate redisTemplate;

    @Mock
    private ReactiveValueOperations<String, String> valueOperations;

    private final AtomicInteger dispatchCount = new AtomicInteger();

    private final GatewayFilterChain chain = exchange -> {
        dispatchCount.incrementAndGet();
        return Mono.empty();
    };

    /** 构造带认证上下文的请求执行（JWT 已通过签名与时效校验的模拟场景）。 */
    private Mono<Void> run(MockServerWebExchange exchange, Jwt jwt) {
        JwtAuthenticationToken authentication = new JwtAuthenticationToken(jwt,
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
        return new JwtBlacklistFilter(redisTemplate).filter(exchange, chain)
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication));
    }

    private Jwt jwt(String jti, Object userId, Object ver) {
        Jwt.Builder builder = Jwt.withTokenValue("access-token")
                .header("alg", "RS256")
                .subject("1")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60));
        if (jti != null) {
            builder.jti(jti);
        }
        if (userId != null) {
            builder.claim("userId", userId);
        }
        if (ver != null) {
            builder.claim("ver", ver);
        }
        return builder.build();
    }

    private MockServerWebExchange exchange() {
        return MockServerWebExchange.from(MockServerHttpRequest.get("/api/user/info"));
    }

    /** 桩：合并 MGET（黑名单值, 版本值），未指定时返回两个 null（均未命中）。 */
    private void stubMultiGet(String blacklistValue, String versionValue) {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(valueOperations.multiGet(anyList()))
                .thenReturn(Mono.just(Arrays.asList(blacklistValue, versionValue)));
    }

    // ========== 401 分支 ==========

    @Test
    @DisplayName("吊销 — jti 命中黑名单 → 401 且不再分发到下游")
    void shouldRejectWith401_whenJtiBlacklisted() {
        MockServerWebExchange exchange = exchange();
        stubMultiGet("1", null);

        run(exchange, jwt(JTI, 1L, 0L)).block();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        assertEquals(0, dispatchCount.get(), "已吊销令牌不得放行");
    }

    @Test
    @DisplayName("吊销 — 令牌版本落后（tokenVer=1，当前=2）→ 401（改密/禁用后全端下线）")
    void shouldRejectWith401_whenTokenVersionStale() {
        MockServerWebExchange exchange = exchange();
        stubMultiGet(null, "2");

        run(exchange, jwt(JTI, 1L, 1L)).block();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        assertEquals(0, dispatchCount.get());
    }

    @Test
    @DisplayName("吊销 — ver claim 缺失（按 0）且版本键存在(1) → 401（存量令牌在用户被吊销后必须失效）")
    void shouldRejectWith401_whenVersionClaimMissingButVersionKeyPresent() {
        MockServerWebExchange exchange = exchange();
        stubMultiGet(null, "1");

        run(exchange, jwt(JTI, 1L, null)).block();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        assertEquals(0, dispatchCount.get());
    }

    // ========== 放行分支 ==========

    @Test
    @DisplayName("放行 — 版本一致（tokenVer=2，当前=2）→ 正常分发一次")
    void shouldPassThrough_whenTokenVersionMatches() {
        MockServerWebExchange exchange = exchange();
        stubMultiGet(null, "2");

        run(exchange, jwt(JTI, 1L, 2L)).block();

        assertEquals(1, dispatchCount.get());
        assertNull(exchange.getResponse().getStatusCode());
    }

    @Test
    @DisplayName("放行 — 版本键缺失（从未用户级吊销）→ 跳过版本比对，正常分发")
    void shouldPassThrough_whenVersionKeyMissing() {
        MockServerWebExchange exchange = exchange();
        stubMultiGet(null, null);

        run(exchange, jwt(JTI, 1L, 0L)).block();

        assertEquals(1, dispatchCount.get());
        assertNull(exchange.getResponse().getStatusCode());
    }

    @Test
    @DisplayName("放行 — ver claim 缺失且版本键缺失 → 存量令牌自然可用（兼容规则）")
    void shouldPassThrough_whenVersionClaimMissingAndVersionKeyMissing() {
        MockServerWebExchange exchange = exchange();
        stubMultiGet(null, null);

        run(exchange, jwt(JTI, 1L, null)).block();

        assertEquals(1, dispatchCount.get());
        assertNull(exchange.getResponse().getStatusCode());
    }

    @Test
    @DisplayName("放行 — ver claim 类型异常 → 按缺失(0)处理，版本键缺失时放行（DEBUG 记录）")
    void shouldPassThrough_whenVersionClaimTypeInvalid() {
        MockServerWebExchange exchange = exchange();
        stubMultiGet(null, null);

        run(exchange, jwt(JTI, 1L, "not-a-number")).block();

        assertEquals(1, dispatchCount.get());
        assertNull(exchange.getResponse().getStatusCode());
    }

    // ========== 边界 / 可用性 ==========

    @Test
    @DisplayName("可用性 — MGET 异常 → fail-open 放行（可用性优先，见类注释权衡）")
    void shouldFailOpen_whenRedisError() {
        MockServerWebExchange exchange = exchange();
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.multiGet(anyList()))
                .thenReturn(Mono.error(new IllegalStateException("redis down")));

        run(exchange, jwt(JTI, 1L, 0L)).block();

        assertEquals(1, dispatchCount.get(), "Redis 故障不得阻断认证链路");
        assertNull(exchange.getResponse().getStatusCode());
    }

    @Test
    @DisplayName("缺 userId — 只查黑名单（单键 MGET，跳过版本比对），黑名单命中仍 401")
    void shouldOnlyCheckBlacklist_whenUserIdMissing() {
        MockServerWebExchange exchange = exchange();
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.multiGet(List.of(BLACKLIST_KEY)))
                .thenReturn(Mono.just(Arrays.asList("1")));

        run(exchange, jwt(JTI, null, null)).block();

        verify(valueOperations).multiGet(List.of(BLACKLIST_KEY));
        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        assertEquals(0, dispatchCount.get());
    }

    @Test
    @DisplayName("缺 userId — 黑名单未命中 → 放行（不查版本键）")
    void shouldPassThrough_whenUserIdMissingAndNotBlacklisted() {
        MockServerWebExchange exchange = exchange();
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.multiGet(List.of(BLACKLIST_KEY)))
                .thenReturn(Mono.just(Arrays.asList((String) null)));

        run(exchange, jwt(JTI, null, null)).block();

        assertEquals(1, dispatchCount.get());
        assertNull(exchange.getResponse().getStatusCode());
    }

    @Test
    @DisplayName("放行 — token 无 jti → 维持原有语义放行且不查 Redis")
    void shouldPassThrough_whenTokenHasNoJti() {
        MockServerWebExchange exchange = exchange();

        run(exchange, jwt(null, 1L, 0L)).block();

        verifyNoInteractions(redisTemplate);
        assertEquals(1, dispatchCount.get());
    }

    @Test
    @DisplayName("放行 — 匿名请求（无安全上下文）→ 不做吊销校验")
    void shouldPassThrough_whenAnonymous() {
        MockServerWebExchange exchange = exchange();

        new JwtBlacklistFilter(redisTemplate).filter(exchange, chain).block();

        verifyNoInteractions(redisTemplate);
        assertEquals(1, dispatchCount.get());
    }
}
