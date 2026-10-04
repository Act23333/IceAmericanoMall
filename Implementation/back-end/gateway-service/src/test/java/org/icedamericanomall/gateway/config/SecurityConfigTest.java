package org.icedamericanomall.gateway.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.WebFilterChainProxy;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * 网关安全链白名单单元测试（方法级口径）。
 *
 * <p>核心断言：浏览类路径仅 GET 匿名放行；同路径的写方法（秒杀下单、商品/分类维护、店铺关注）
 * 与已删除的 legacy 认证路径一律要求认证。无法起真实下游，故以安全链 + 终止 WebFilterChain 模拟。
 */
@DisplayName("SecurityConfig 白名单方法级单元测试")
class SecurityConfigTest {

    /** 认证服务 JWKS 地址（测试占位，不会真正发起请求）。 */
    private static final String JWKS_URI = "http://localhost:9000/oauth2/jwks";

    private final AtomicInteger passedCount = new AtomicInteger();

    /** 复现生产装配：SecurityWebFilterChain 由 WebFilterChainProxy 统一驱动。 */
    private WebFilterChainProxy securityChainProxy;

    /** 安全链之后的终止节点：计数即代表请求被放行。 */
    private final WebFilterChain terminator = exchange -> {
        passedCount.incrementAndGet();
        return Mono.empty();
    };

    @BeforeEach
    void setUp() {
        passedCount.set(0);
        SecurityConfig config = new SecurityConfig(JWKS_URI, List.of("http://localhost:5173"));
        SecurityWebFilterChain chain = config.springSecurityFilterChain(ServerHttpSecurity.http(),
                mock(ReactiveJwtDecoder.class));
        securityChainProxy = new WebFilterChainProxy(chain);
    }

    private void exchange(HttpMethod method, String path) {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.method(method, path));
        securityChainProxy.filter(exchange, terminator).block();
    }

    @ParameterizedTest(name = "{0} {1} 匿名可访问")
    @CsvSource({
            "GET,  /api/item/product/1",
            "GET,  /api/item/category/tree",
            "GET,  /api/item/review/product/1",
            "GET,  /api/home/config",
            "GET,  /api/search/product",
            "GET,  /api/shop/100",
            "GET,  /api/shops/1",
            "GET,  /api/flash",
            "GET,  /api/flash/list",
            "GET,  /api/coupon/template",
            "GET,  /api/ai/chat/sessions",
            "POST, /api/auth/web/login",
            "POST, /api/verification/codes",
            "POST, /api/pay/callback/wechat",
            "POST, /api/ai/chat"
    })
    @DisplayName("放行 — 浏览类 GET 与匿名写入口")
    void shouldAllow_whenPublicEndpoint(HttpMethod method, String path) {
        exchange(method, path);

        assertEquals(1, passedCount.get(), method + " " + path + " 应被放行");
    }

    @ParameterizedTest(name = "{0} {1} 匿名被拒")
    @CsvSource({
            "POST,   /api/flash/buy",
            "POST,   /api/item/product",
            "PUT,    /api/item/product/1",
            "POST,   /api/item/category",
            "POST,   /api/shop/follow",
            "DELETE, /api/shop/follow",
            "GET,    /api/user/info",
            "GET,    /api/cart/list",
            // WebSocket 握手路径：刻意不在白名单内，令牌由 WsHandshakeTokenFilter 换头后仍走认证（ADR-026）
            "GET,    /ws/chat/123/abc/websocket",
            "GET,    /ws/chat/info",
            // 已删除的 legacy 认证路径：不再匿名放行
            "POST,   /api/auth/login",
            "POST,   /api/auth/login/phone",
            "POST,   /api/auth/login/wechat",
            "POST,   /api/auth/captcha/sms"
    })
    @DisplayName("拒绝 — 写端点与需登录路径（匿名 401/403）")
    void shouldDeny_whenWriteOrAuthenticatedEndpoint(HttpMethod method, String path) {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.method(method, path));

        securityChainProxy.filter(exchange, terminator).block();

        assertEquals(0, passedCount.get(), method + " " + path + " 不得匿名放行");
        assertTrue(exchange.getResponse().getStatusCode() != null
                        && exchange.getResponse().getStatusCode().value() >= 401,
                method + " " + path + " 应返回 401/403，实际=" + exchange.getResponse().getStatusCode());
    }

    @Test
    @DisplayName("白名单口径 — 只读浏览路径与匿名写入口均为显式枚举，写白名单不含浏览类通配")
    void shouldDeclareExplicitWhitelists() {
        assertTrue(SecurityConfig.PUBLIC_GET_PATHS.length > 0);
        assertTrue(SecurityConfig.PUBLIC_WRITE_PATHS.length > 0);
        assertFalse(Arrays.stream(SecurityConfig.PUBLIC_WRITE_PATHS)
                        .anyMatch(path -> path.equals("/api/item/**") || path.equals("/api/flash/**")
                                || path.equals("/api/shop/**")),
                "匿名写白名单不得包含浏览类通配路径（否则写端点会重新变为匿名可打）");
    }
}
