package org.icedamericanomall.gateway.filter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WebSocket 握手令牌边缘换头过滤器单元测试（ADR-026）。
 *
 * <p>覆盖：生效路径 + query 令牌 → 换 {@code Authorization: Bearer} 头且剔除 query 令牌参数
 * （其余参数逐字保留）；已有 {@code Authorization} → 不覆盖、URI 原样；非生效路径 → 完全不动；
 * 无令牌 / 空令牌 → 原样放行（后续安全链负责 401）；过滤器顺序早于 Spring Security 安全链。
 */
@DisplayName("WsHandshakeTokenFilter 单元测试")
class WsHandshakeTokenFilterTest {

    /** 前端约定的握手令牌参数名。 */
    private static final String TOKEN_PARAM = "token";

    /** 测试令牌值。 */
    private static final String TOKEN = "header.payload.signature";

    /** 生产生效路径配置。 */
    private static final List<String> TOKEN_PATHS = List.of("/ws/chat/**");

    /**
     * Spring Security 安全链（WebFilterChainProxy）的 WebFilter 顺序，
     * 取自 Spring Security {@code WebFluxSecurityConfiguration.WEB_FILTER_CHAIN_FILTER_ORDER = -100}。
     */
    private static final int SECURITY_CHAIN_ORDER = -100;

    private final WsHandshakeTokenFilter filter = new WsHandshakeTokenFilter(TOKEN_PATHS);

    /** 捕获链路中的 exchange，返回其请求（与后续过滤器所见一致）。 */
    private ServerHttpRequest runFilter(MockServerWebExchange exchange) {
        AtomicReference<ServerWebExchange> captured = new AtomicReference<>();
        WebFilterChain chain = capturedExchange -> {
            captured.set(capturedExchange);
            return Mono.empty();
        };
        filter.filter(exchange, chain).block();
        return captured.get().getRequest();
    }

    @Test
    @DisplayName("换头 — WS 握手路径携带 query 令牌且无 Authorization → 头设为 Bearer 且 query 令牌被剔除")
    void shouldSwapToken_whenWsHandshakeWithQueryToken() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("http://localhost:8080/ws/chat/123/abc/websocket?token="
                        + TOKEN + "&userId=7"));

        ServerHttpRequest request = runFilter(exchange);

        assertEquals("Bearer " + TOKEN, request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION));
        assertEquals("userId=7", request.getURI().getRawQuery(), "query 中的令牌参数必须被剔除");
        assertFalse(request.getQueryParams().containsKey(TOKEN_PARAM), "下游不得再看到 token 参数");
        assertEquals("7", request.getQueryParams().getFirst("userId"), "其余参数逐字保留");
        assertTrue(request.getURI().getPath().endsWith("/ws/chat/123/abc/websocket"),
                "路径不得被改写");
    }

    @Test
    @DisplayName("剔除 — 令牌是唯一 query 参数时 → 重构后 URI 不带 query（不留空 ?）")
    void shouldDropQuery_whenTokenIsTheOnlyParam() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("http://localhost:8080/ws/chat/info?token=" + TOKEN));

        ServerHttpRequest request = runFilter(exchange);

        assertEquals("Bearer " + TOKEN, request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION));
        assertNull(request.getURI().getRawQuery(), "令牌剔除后不应残留空 query");
    }

    @Test
    @DisplayName("不覆盖 — 已有 Authorization 头时原样放行（URI 亦不改写）")
    void shouldNotOverrideAuthorization_whenHeaderAlreadyPresent() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("http://localhost:8080/ws/chat/info?token=" + TOKEN)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer existing-token"));

        ServerHttpRequest request = runFilter(exchange);

        assertEquals("Bearer existing-token", request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION));
        assertEquals(TOKEN_PARAM + "=" + TOKEN, request.getURI().getRawQuery(),
                "已有 Authorization 时不得改写 URI（原样放行）");
    }

    @Test
    @DisplayName("不生效 — 非配置路径即使带 token 参数也不换头（避免误伤普通接口）")
    void shouldNotTouchRequest_whenPathNotConfigured() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("http://localhost:8080/api/user/info?token=" + TOKEN + "&userId=7"));

        ServerHttpRequest request = runFilter(exchange);

        assertNull(request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION));
        assertEquals(TOKEN_PARAM + "=" + TOKEN + "&userId=7", request.getURI().getRawQuery());
    }

    @Test
    @DisplayName("放行 — 生效路径但无令牌 → 不注入 Authorization（401 交给后续安全链）")
    void shouldPassThrough_whenTokenAbsent() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("http://localhost:8080/ws/chat/123/abc/websocket?userId=7"));

        ServerHttpRequest request = runFilter(exchange);

        assertNull(request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION), "无令牌不得伪造 Bearer 头");
        assertEquals("userId=7", request.getURI().getRawQuery());
    }

    @Test
    @DisplayName("放行 — 令牌参数存在但为空值 → 视为无令牌，既不起头也不改写 URI")
    void shouldPassThrough_whenTokenBlank() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.method(HttpMethod.GET,
                        URI.create("http://localhost:8080/ws/chat/info?token=&userId=7")));

        ServerHttpRequest request = runFilter(exchange);

        assertNull(request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION));
        assertEquals("token=&userId=7", request.getURI().getRawQuery());
    }

    @Test
    @DisplayName("剔除 — 参数名被百分号编码（%74oken）时同样按令牌参数剔除（防手工构造绕过）")
    void shouldStripEncodedTokenParam_whenParamNameEscaped() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.method(HttpMethod.GET,
                        URI.create("http://localhost:8080/ws/chat/info?%74oken=" + TOKEN)));

        ServerHttpRequest request = runFilter(exchange);

        assertEquals("Bearer " + TOKEN, request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION));
        assertNull(request.getURI().getRawQuery(), "编码形态的令牌参数名同样必须被剔除");
    }

    @Test
    @DisplayName("过滤器顺序 — 早于 Spring Security 安全链（换头必须发生在认证之前）")
    void shouldRunBeforeSecurityChain() {
        assertTrue(filter.getOrder() < SECURITY_CHAIN_ORDER,
                "握手换头必须早于 Spring Security 安全链(order=" + SECURITY_CHAIN_ORDER + ")，实际="
                        + filter.getOrder());
        assertTrue(filter.getOrder() < 0, "必须在链路前段执行");
    }
}
