package org.icedamericanomall.gateway.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.PropertySourcesPropertyResolver;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code application.yml} 网关路由与限流配置解析测试。
 *
 * <p>覆盖：{@code /api/shops/**} 公开店铺页谓词、auth/verification 路由的 RequestRateLimiter
 * 参数（占位符可从 {@code gateway.rate-limit.*} 解析）、JWKS 地址占位符默认值、
 * 已清理的无效配置项（{@code server.servlet.context-path}、DEBUG 日志）不再出现。
 */
@DisplayName("application.yml 网关路由与限流配置测试")
class GatewayRouteConfigTest {

    private static final String ROUTE_PREFIX = "spring.cloud.gateway.server.webflux.routes";

    /** 扫描路由下标的上限（防御性，避免无限循环）。 */
    private static final int MAX_ROUTE_SCAN = 64;

    private PropertySourcesPropertyResolver resolver;

    @BeforeEach
    void setUp() throws IOException {
        MutablePropertySources sources = new MutablePropertySources();
        List<PropertySource<?>> yamlSources = new YamlPropertySourceLoader()
                .load("application.yml", new ClassPathResource("application.yml"));
        yamlSources.forEach(sources::addLast);
        resolver = new PropertySourcesPropertyResolver(sources);
    }

    /** 按路由 id 找到其在配置中的下标（对路由顺序不敏感）。 */
    private int routeIndexOf(String routeId) {
        for (int i = 0; i < MAX_ROUTE_SCAN; i++) {
            String id = resolver.getProperty(ROUTE_PREFIX + "[" + i + "].id");
            if (id == null) {
                break;
            }
            if (id.equals(routeId)) {
                return i;
            }
        }
        throw new AssertionError("未找到路由配置: " + routeId);
    }

    /** 收集 {@code keyPrefix[0..n]} 形式的配置值。 */
    private List<String> indexedValues(String keyPrefix) {
        List<String> values = new ArrayList<>();
        for (int i = 0; i < MAX_ROUTE_SCAN; i++) {
            String value = resolver.getProperty(keyPrefix + "[" + i + "]");
            if (value == null) {
                break;
            }
            values.add(value);
        }
        return values;
    }

    private List<String> predicatesOf(String routeId) {
        return indexedValues(ROUTE_PREFIX + "[" + routeIndexOf(routeId) + "].predicates");
    }

    @Test
    @DisplayName("路由 — merchant-service 覆盖 /api/shops/**（ADR-019 公开店铺页）")
    void shouldRouteShopsPath_whenMerchantRouteConfigured() {
        List<String> predicates = predicatesOf("merchant-service");

        assertTrue(predicates.contains("Path=/api/shops/**"),
                "merchant-service 路由必须包含 /api/shops/** 谓词，实际=" + predicates);
        assertTrue(predicates.contains("Path=/api/shop/**"), "遗留 sellerId 口径路径仍需保留");
    }

    @Test
    @DisplayName("限流 — auth 路由按 IP 限流且阈值来自可配置属性（占位符已解析）")
    void shouldResolveAuthRateLimiterArgs() {
        String prefix = ROUTE_PREFIX + "[" + routeIndexOf("auth-service") + "].filters[0]";

        assertEquals("RequestRateLimiter", resolver.getProperty(prefix + ".name"));
        assertEquals("#{@ipKeyResolver}", resolver.getProperty(prefix + ".args.key-resolver"));
        assertEquals("20", resolver.getProperty(prefix + ".args.redis-rate-limiter.replenishRate"));
        assertEquals("40", resolver.getProperty(prefix + ".args.redis-rate-limiter.burstCapacity"));
        assertEquals("20", resolver.getProperty("gateway.rate-limit.auth.replenish-rate"));
    }

    @Test
    @DisplayName("限流 — verification 路由按 IP 更严格限流（防短信轰炸）")
    void shouldResolveVerificationRateLimiterArgs() {
        String prefix = ROUTE_PREFIX + "[" + routeIndexOf("verification-service") + "].filters[0]";

        assertEquals("RequestRateLimiter", resolver.getProperty(prefix + ".name"));
        assertEquals("#{@ipKeyResolver}", resolver.getProperty(prefix + ".args.key-resolver"));
        assertEquals("5", resolver.getProperty(prefix + ".args.redis-rate-limiter.replenishRate"));
        assertEquals("10", resolver.getProperty(prefix + ".args.redis-rate-limiter.burstCapacity"));
    }

    @Test
    @DisplayName("清理 — JWKS 地址占位符带本地默认值，且 WebFlux 无效的 context-path / DEBUG 日志已移除")
    void shouldCleanupInvalidConfigEntries() {
        assertEquals("http://localhost:9000/oauth2/jwks",
                resolver.getProperty("spring.security.oauth2.resourceserver.jwt.jwk-set-uri"));
        assertNull(resolver.getProperty("server.servlet.context-path"), "WebFlux 下 context-path 无效，应删除");
        assertEquals("INFO", resolver.getProperty("logging.level.org.springframework.cloud.gateway"));
        assertEquals("INFO", resolver.getProperty("logging.level.org.springframework.security"));
    }

    @Test
    @DisplayName("路由 — /api/admin/pay/** 指向 pay-service 且声明在 /api/admin/** 兜底路由之前（F1/F4 前置条件）")
    void shouldRouteAdminPayBeforeAdminFallback() {
        List<String> predicates = predicatesOf("pay-admin-routes");

        assertTrue(predicates.contains("Path=/api/admin/pay/**"),
                "支付管理路由必须覆盖 /api/admin/pay/**，实际=" + predicates);
        assertEquals("lb://pay-service",
                resolver.getProperty(ROUTE_PREFIX + "[" + routeIndexOf("pay-admin-routes") + "].uri"));

        // 顺序不变量：专属路由必须先于兜底 /api/admin/**（后者指向 user-service），否则请求会被吞到 user-service 而 404
        assertTrue(routeIndexOf("pay-admin-routes") < routeIndexOf("admin-user-routes"),
                "pay-admin-routes 必须声明在 admin-user-routes 之前");
    }
}
