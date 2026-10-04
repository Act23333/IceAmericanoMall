package org.icedamericanomall.gateway.filter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 入站伪造头剥离 + 客户端 IP 覆写过滤器单元测试（安全加固）。
 *
 * <p>覆盖：伪造上下文头全量剥离（含公开路径）、可伪造代理头（XFF/X-Real-IP/Forwarded）剥离、
 * {@code X-Client-Ip} 覆写为真实对端地址、合法头保留（traceId）、无伪造头时请求头不受影响。
 */
@DisplayName("UntrustedHeaderSanitizerFilter 单元测试")
class UntrustedHeaderSanitizerFilterTest {

    /** 伪造请求里声明的"客户端 IP"（必须被丢弃）。 */
    private static final String FORGED_IP = "1.2.3.4";

    /** 模拟 TCP 对端地址（网关看到的真实来源）。 */
    private static final String REAL_REMOTE_IP = "10.1.2.3";

    private final UntrustedHeaderSanitizerFilter filter = new UntrustedHeaderSanitizerFilter();

    /** 捕获链路中的 exchange，返回其请求。 */
    private ServerHttpRequest runFilter(MockServerWebExchange exchange) {
        AtomicReference<ServerWebExchange> captured = new AtomicReference<>();
        GatewayFilterChain chain = capturedExchange -> {
            captured.set(capturedExchange);
            return Mono.empty();
        };
        filter.filter(exchange, chain).block();
        return captured.get().getRequest();
    }

    @Test
    @DisplayName("剥离 — 公开路径携带伪造 X-User-Id/X-User-Roles 等头 → 全部移除且 X-Client-Ip 覆写为真实对端地址")
    void shouldStripForgedHeaders_whenPresent() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/verification/codes")
                        .remoteAddress(new InetSocketAddress(REAL_REMOTE_IP, 12345))
                        .header("X-User-Id", "999")
                        .header("X-Username", "hacker")
                        .header("X-User-Roles", "ROLE_ADMIN")
                        .header("X-User-Permissions", "user:admin")
                        .header("X-Tenant-Id", "evil")
                        .header("X-Client-Ip", FORGED_IP));

        ServerHttpRequest request = runFilter(exchange);

        UntrustedHeaderSanitizerFilter.TRUSTED_CONTEXT_HEADERS.stream()
                .filter(header -> !UntrustedHeaderSanitizerFilter.CLIENT_IP_HEADER.equals(header))
                .forEach(header -> assertNull(request.getHeaders().getFirst(header), header + " 必须被剥离"));
        // X-Client-Ip 不再来自入站，而是由网关按真实 TCP 对端地址覆写
        assertEquals(REAL_REMOTE_IP, request.getHeaders().getFirst(UntrustedHeaderSanitizerFilter.CLIENT_IP_HEADER));
    }

    @Test
    @DisplayName("剥离 — 伪造 X-Forwarded-For/X-Real-IP/Forwarded → 全部移除（下游只能取到网关注入的真实 IP）")
    void shouldStripSpoofableProxyHeaders_whenPresent() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/verification/codes")
                        .remoteAddress(new InetSocketAddress(REAL_REMOTE_IP, 12345))
                        .header("X-Forwarded-For", FORGED_IP + ", 5.6.7.8")
                        .header("X-Real-IP", FORGED_IP)
                        .header("Forwarded", "for=" + FORGED_IP));

        ServerHttpRequest request = runFilter(exchange);

        UntrustedHeaderSanitizerFilter.SPOOFABLE_IP_HEADERS
                .forEach(header -> assertNull(request.getHeaders().getFirst(header), header + " 必须被剥离"));
        assertEquals(REAL_REMOTE_IP, request.getHeaders().getFirst(UntrustedHeaderSanitizerFilter.CLIENT_IP_HEADER));
    }

    @Test
    @DisplayName("覆写 — 未携带任何伪造头时仍注入真实 X-Client-Ip（匿名请求同样不可伪造来源）")
    void shouldOverwriteClientIp_whenNoForgedHeaders() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/item/product/1")
                        .remoteAddress(new InetSocketAddress(REAL_REMOTE_IP, 12345))
                        .header("Accept", "application/json"));

        ServerHttpRequest request = runFilter(exchange);

        assertEquals(REAL_REMOTE_IP, request.getHeaders().getFirst(UntrustedHeaderSanitizerFilter.CLIENT_IP_HEADER));
        assertEquals("application/json", request.getHeaders().getFirst("Accept"));
    }

    @Test
    @DisplayName("保留 — X-Trace-Id 允许上游透传（链路追踪语义不变）")
    void shouldKeepTraceId_whenPresent() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/user/info")
                        .header("X-Trace-Id", "trace-abc")
                        .header("X-User-Id", "999"));

        ServerHttpRequest request = runFilter(exchange);

        assertEquals("trace-abc", request.getHeaders().getFirst("X-Trace-Id"));
        assertNull(request.getHeaders().getFirst("X-User-Id"));
    }

    @Test
    @DisplayName("透传 — 未携带伪造头时业务请求头不受影响（正常请求零副作用）")
    void shouldPassThrough_whenNoForgedHeaders() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/item/product/1")
                        .header("Accept", "application/json"));

        ServerHttpRequest request = runFilter(exchange);

        assertEquals("application/json", request.getHeaders().getFirst("Accept"));
    }

    @Test
    @DisplayName("过滤器顺序 — 早于 TraceIdFilter、黑名单校验与用户上下文注入（可信注入之前的第一个过滤器）")
    void shouldRunBeforeAllOtherFilters() {
        assertTrue(filter.getOrder() < new TraceIdFilter().getOrder(),
                "伪造头剥离必须发生在 TraceIdFilter 之前");
        assertTrue(filter.getOrder() < new UserContextHeaderFilter(null).getOrder(),
                "伪造头剥离必须发生在用户上下文注入之前");
        assertTrue(filter.getOrder() < new JwtBlacklistFilter(null).getOrder(),
                "伪造头剥离必须发生在吊销校验之前");
    }
}
