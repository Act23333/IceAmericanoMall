package org.icedamericanomall.common.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Redis 相关自动配置的 {@code @ConditionalOnClass} 守卫验证（R-1 遗留项回归测试）。
 *
 * <p>背景：ia-common 对 {@code spring-boot-starter-data-redis} 的依赖是 optional，
 * 但 {@code RedisConfig} / {@code RedisLuaScriptConfig} / {@code RateLimitUtils} /
 * {@code RateLimitAspect} 此前无条件注册。未引入 Redis 的服务
 * （典型：notification-service）会在启动时抛 {@code NoClassDefFoundError}。
 *
 * <p>验证手段：{@link FilteredClassLoader} 把 Redis 相关类从类路径上「藏掉」，
 * 条件注解应判定为不匹配、整体跳过配置，上下文照常启动。
 */
@DisplayName("条件装配 — Redis 类路径守卫")
class RedisConditionGuardTest {

    /** Redis 相关类整体缺席时需要隐藏的类。 */
    private static final Class<?>[] REDIS_CLASSES = {
            RedisConnectionFactory.class,
            RedisTemplate.class,
            StringRedisTemplate.class,
            RedisScript.class
    };

    @Test
    @DisplayName("类路径无 Redis → RedisConfig 与 RedisLuaScriptConfig 整体跳过，上下文正常启动")
    void shouldSkipRedisConfigs_whenRedisClassesAbsent() {
        new ApplicationContextRunner()
                .withClassLoader(new FilteredClassLoader(REDIS_CLASSES))
                .withUserConfiguration(RedisConfig.class, RedisLuaScriptConfig.class)
                .run(context -> {
                    assertThat(context.getStartupFailure())
                            .as("无 Redis 依赖时必须能正常启动，而不是 NoClassDefFoundError")
                            .isNull();
                    assertThat(context.containsBean("redisTemplate"))
                            .as("RedisConfig 应被条件注解跳过").isFalse();
                    assertThat(context.containsBean("rateLimitRedisScript"))
                            .as("RedisLuaScriptConfig 应被条件注解跳过").isFalse();
                });
    }

    @Test
    @DisplayName("类路径有 Redis → 注册 RedisTemplate 与限流 Lua 脚本 Bean（守卫未过度收紧）")
    void shouldRegisterRedisConfigs_whenRedisClassesPresent() {
        new ApplicationContextRunner()
                .withUserConfiguration(RedisConfig.class, RedisLuaScriptConfig.class)
                .withBean(RedisConnectionFactory.class, () -> mock(RedisConnectionFactory.class))
                .run(context -> {
                    assertThat(context.getStartupFailure()).isNull();
                    assertThat(context.containsBean("redisTemplate")).isTrue();
                    assertThat(context.getBeanNamesForType(RedisScript.class))
                            .as("仅 rate_limit.lua（V5.3：del_redisKey/check_limit/login_rate_limit 已随零调用清理）")
                            .hasSize(1);
                });
    }
}
