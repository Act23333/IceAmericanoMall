package org.icedamericanomall.gateway.filter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * AI 每日额度过滤器单元测试。
 *
 * <p>覆盖：首次计数补挂 TTL、已存在计数不刷新 TTL、超额 429 且恰好分发一次（回归
 * {@code switchIfEmpty} 导致的双重路由缺陷）、匿名按 IP 维度计数、Redis 异常 fail-open、
 * 非 AI 路径不计数。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AiTokenQuotaFilter 单元测试")
class AiTokenQuotaFilterTest {

    private static final long DAILY_LIMIT = 1000L;
    private static final int BYTES_TO_TOKENS_FACTOR = 2;
    private static final int DEFAULT_TOKENS = 100;
    private static final String REJECT_MESSAGE = "今日额度已用尽";

    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String ANONYMOUS_IP = "10.9.8.7";

    @Mock
    private ReactiveRedisTemplate<String, String> redisTemplate;

    @Mock
    private ReactiveValueOperations<String, String> valueOps;

    private final AtomicInteger dispatchCount = new AtomicInteger();

    /** 记录下游分发次数的 chain（正常放行时恰好一次）。 */
    private final GatewayFilterChain chain = exchange -> {
        dispatchCount.incrementAndGet();
        return Mono.empty();
    };

    private AiTokenQuotaFilter newFilter() {
        return new AiTokenQuotaFilter(redisTemplate, DAILY_LIMIT, BYTES_TO_TOKENS_FACTOR,
                DEFAULT_TOKENS, REJECT_MESSAGE);
    }

    private String quotaKey(String subject) {
        return "ai:quota:" + subject + ":" + LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
    }

    @Test
    @DisplayName("计数 — 首次请求原子 INCR 并补挂次日零点 TTL，下游恰好分发一次")
    void shouldCountOnceAndDispatchOnce_whenFirstRequestUnderQuota() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/ai/chat").header(USER_ID_HEADER, "7"));
        String key = quotaKey("u:7");
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment(eq(key), anyLong())).thenReturn(Mono.just((long) DEFAULT_TOKENS));
        when(redisTemplate.expireAt(eq(key), any(Instant.class))).thenReturn(Mono.just(true));

        newFilter().filter(exchange, chain).block();

        verify(valueOps).increment(key, DEFAULT_TOKENS);
        verify(redisTemplate).expireAt(eq(key), any(Instant.class));
        assertEquals(1, dispatchCount.get(), "chain.filter() 必须恰好执行一次");
        assertNull(exchange.getResponse().getStatusCode(), "未超额不应写入响应状态");
    }

    @Test
    @DisplayName("计数 — 计数键已存在时只累加、不重设 TTL")
    void shouldNotResetTtl_whenCounterExists() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/ai/chat").header(USER_ID_HEADER, "7"));
        String key = quotaKey("u:7");
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment(eq(key), anyLong())).thenReturn(Mono.just(300L));

        newFilter().filter(exchange, chain).block();

        verify(redisTemplate, never()).expireAt(anyString(), any(Instant.class));
        assertEquals(1, dispatchCount.get());
    }

    @Test
    @DisplayName("超额 — 用量超过阈值 → 429 拒绝且不再分发（杜绝 switchIfEmpty 双重路由）")
    void shouldRejectWith429_whenQuotaExceeded() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/ai/chat").header(USER_ID_HEADER, "7"));
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment(anyString(), anyLong())).thenReturn(Mono.just(DAILY_LIMIT + 1));

        newFilter().filter(exchange, chain).block();

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, exchange.getResponse().getStatusCode());
        assertEquals(0, dispatchCount.get(), "拒绝后不得再分发到下游");
        assertTrue(exchange.getResponse().getBodyAsString().block().contains(REJECT_MESSAGE));
    }

    @Test
    @DisplayName("匿名 — 无 X-User-Id 时按真实客户端 IP 维度计数（不再无限放行匿名流量）")
    void shouldCountByClientIp_whenAnonymous() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/ai/chat")
                        .remoteAddress(new InetSocketAddress(ANONYMOUS_IP, 5000)));
        String key = quotaKey("ip:" + ANONYMOUS_IP);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment(eq(key), anyLong())).thenReturn(Mono.just((long) DEFAULT_TOKENS));
        when(redisTemplate.expireAt(eq(key), any(Instant.class))).thenReturn(Mono.just(true));

        newFilter().filter(exchange, chain).block();

        verify(valueOps).increment(key, DEFAULT_TOKENS);
        assertEquals(1, dispatchCount.get());
    }

    @Test
    @DisplayName("可用性 — Redis 计数异常时 fail-open 放行（不阻断 AI 链路）")
    void shouldFailOpen_whenRedisError() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/ai/chat").header(USER_ID_HEADER, "7"));
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment(anyString(), anyLong()))
                .thenReturn(Mono.error(new IllegalStateException("redis down")));

        newFilter().filter(exchange, chain).block();

        assertEquals(1, dispatchCount.get());
        assertNull(exchange.getResponse().getStatusCode());
    }

    @Test
    @DisplayName("成本估算 — 携带 Content-Length 时按 字节数 × 系数 计数")
    void shouldEstimateTokensFromContentLength_whenPresent() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/ai/chat")
                        .header(USER_ID_HEADER, "7")
                        .header(HttpHeaders.CONTENT_LENGTH, "50"));
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment(anyString(), anyLong())).thenReturn(Mono.just(100L));

        newFilter().filter(exchange, chain).block();

        verify(valueOps).increment(anyString(), eq(50L * BYTES_TO_TOKENS_FACTOR));
        assertEquals(1, dispatchCount.get());
    }

    @Test
    @DisplayName("范围 — 非 /api/ai 路径不计数、不查 Redis")
    void shouldSkipQuota_whenNonAiPath() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/user/info").header(USER_ID_HEADER, "7"));

        newFilter().filter(exchange, chain).block();

        verifyNoInteractions(redisTemplate);
        assertEquals(1, dispatchCount.get());
    }
}
