package org.icedamericanomall.gateway.filter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 请求体缓存过滤器单元测试。
 *
 * <p>覆盖：正常请求体可被下游重复读取且只分发一次、超过缓冲上限返回 413、GET 请求不缓存。
 */
@DisplayName("CacheBodyGlobalFilter 单元测试")
class CacheBodyGlobalFilterTest {

    private static final byte[] PAYLOAD = "{\"nickname\":\"abc\"}".getBytes(StandardCharsets.UTF_8);

    private final AtomicInteger dispatchCount = new AtomicInteger();

    private final AtomicReference<ServerWebExchange> captured = new AtomicReference<>();

    private final GatewayFilterChain chain = exchange -> {
        captured.set(exchange);
        dispatchCount.incrementAndGet();
        return Mono.empty();
    };

    /** 读取下游可见的请求体内容。 */
    private String readBody(ServerWebExchange exchange) {
        DataBuffer buffer = DataBufferUtils.join(exchange.getRequest().getBody()).block();
        byte[] bytes = new byte[buffer.readableByteCount()];
        buffer.read(bytes);
        DataBufferUtils.release(buffer);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("缓存 — POST 请求体被缓存且下游可读，恰好分发一次")
    void shouldCacheBodyAndDispatchOnce_whenPostWithBody() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/user/profile").body(new String(PAYLOAD, StandardCharsets.UTF_8)));

        new CacheBodyGlobalFilter(true, 1024).filter(exchange, chain).block();

        assertEquals(1, dispatchCount.get());
        assertEquals(new String(PAYLOAD, StandardCharsets.UTF_8), readBody(captured.get()));
    }

    @Test
    @DisplayName("超限 — 请求体超过缓冲上限 → 413 且不分发到下游")
    void shouldRejectWith413_whenBodyExceedsLimit() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/user/profile").body(new String(PAYLOAD, StandardCharsets.UTF_8)));

        new CacheBodyGlobalFilter(true, 4).filter(exchange, chain).block();

        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, exchange.getResponse().getStatusCode());
        assertEquals(0, dispatchCount.get(), "超限请求不得进入下游");
    }

    @Test
    @DisplayName("关闭开关 — gateway.body-cache.enabled=false 时不缓存，直接分发")
    void shouldSkipCaching_whenDisabled() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/user/profile").body(new String(PAYLOAD, StandardCharsets.UTF_8)));

        new CacheBodyGlobalFilter(false, 1024).filter(exchange, chain).block();

        assertEquals(1, dispatchCount.get());
        assertNull(exchange.getResponse().getStatusCode());
    }

    @Test
    @DisplayName("非缓存方法 — GET 请求体不缓存、直接分发")
    void shouldSkipCaching_whenGetRequest() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/item/product/1"));

        new CacheBodyGlobalFilter(true, 1024).filter(exchange, chain).block();

        assertEquals(1, dispatchCount.get());
        assertNull(exchange.getResponse().getStatusCode());
    }
}
