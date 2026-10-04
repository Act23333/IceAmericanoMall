package org.icedamericanomall.user.infrastructure.config;

import org.icedamericanomall.fallback.AuthContextClientFallback;
import org.icedamericanomall.fallback.PointsClientFallback;
import org.icedamericanomall.user.UserApplication;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Feign 熔断降级的配置层验证（R6 / V5.4 熔断铺设）。
 *
 * <p>本服务使用 Feign（{@code AuthContextClient} / {@code PointsClient}，契约在 ia-api）而此前
 * 未开熔断；本测试把「fallback 真实生效」所需的三个前提逐条钉死，任一被回退即失败：
 * <ol>
 *   <li>依赖存在：resilience4j 的 {@code CircuitBreaker} 在测试类路径上（starter 已引入）</li>
 *   <li>开关打开：{@code spring.cloud.openfeign.circuitbreaker.enabled=true}
 *       —— 未开启时 Feign 走 DefaultTargeter，fallbackFactory 被静默忽略</li>
 *   <li>Bean 可达：组件扫描包含 {@code org.icedamericanomall.fallback}
 *       且各 fallbackFactory 带 {@code @Component}（开启熔断后 Feign 需要取到这些 Bean，ADR-009）</li>
 * </ol>
 *
 * <p>另锁死 TimeLimiter 默认超时已放宽（缺省 1s 会把设置角色的写库慢调用误判为失败，走 fail-closed 降级）。
 */
@DisplayName("Feign 熔断降级配置测试（user-service）")
class FeignCircuitBreakerConfigTest {

    private static final String CIRCUIT_BREAKER_PROPERTY =
            "spring.cloud.openfeign.circuitbreaker.enabled";

    @Test
    @DisplayName("依赖 — resilience4j CircuitBreaker 在类路径上（starter 已引入）")
    void shouldHaveResilience4jOnClasspath() {
        assertTrue(isClassPresent("io.github.resilience4j.circuitbreaker.CircuitBreaker"),
                "缺少 spring-cloud-starter-circuitbreaker-resilience4j 依赖");
    }

    @Test
    @DisplayName("开关 — application.yml 开启 feign circuitbreaker")
    void shouldEnableCircuitBreakerInApplicationYml() throws Exception {
        ClassPathResource resource = new ClassPathResource("application.yml");
        assertTrue(resource.exists(), "找不到 application.yml");

        String value = readYamlProperty(resource, CIRCUIT_BREAKER_PROPERTY);
        assertNotNull(value, "未配置 " + CIRCUIT_BREAKER_PROPERTY);
        assertTrue(Boolean.parseBoolean(value),
                CIRCUIT_BREAKER_PROPERTY + " 必须为 true，否则 fallbackFactory 不会生效");
    }

    @Test
    @DisplayName("超时 — TimeLimiter 默认超时已放宽至 5s")
    void shouldConfigureTimeLimiterDefault() throws Exception {
        String value = readYamlProperty(new ClassPathResource("application.yml"),
                "resilience4j.timelimiter.configs.default.timeout-duration");

        assertNotNull(value, "未配置 resilience4j.timelimiter.configs.default.timeout-duration");
        assertEquals("5s", value);
    }

    @Test
    @DisplayName("Bean 可达 — 组件扫描包含 fallback 包，且本服务用到的降级工厂均为 @Component")
    void shouldScanFallbackPackageAndRegisterFallbackFactories() {
        SpringBootApplication annotation = UserApplication.class
                .getAnnotation(SpringBootApplication.class);
        assertNotNull(annotation, "启动类缺少 @SpringBootApplication");

        assertTrue(Arrays.asList(annotation.scanBasePackages()).contains("org.icedamericanomall.fallback"),
                "组件扫描必须包含 org.icedamericanomall.fallback，否则熔断开启后取不到 fallbackFactory Bean");

        List<Class<?>> fallbackFactories = List.of(
                AuthContextClientFallback.class, PointsClientFallback.class);
        for (Class<?> fallbackFactory : fallbackFactories) {
            assertTrue(fallbackFactory.isAnnotationPresent(Component.class),
                    fallbackFactory.getSimpleName() + " 必须标注 @Component 才能被扫描为 Bean");
        }
    }

    /** 读取 application.yml 中的扁平化属性值 */
    private String readYamlProperty(ClassPathResource resource, String key) throws Exception {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader().load("application.yml", resource);
        for (PropertySource<?> source : sources) {
            Object value = source.getProperty(key);
            if (value != null) {
                return String.valueOf(value);
            }
        }
        return null;
    }

    private boolean isClassPresent(String className) {
        try {
            Class.forName(className, false, getClass().getClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
