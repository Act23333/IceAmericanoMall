package org.icedamericanomall.common.aspect;

import org.icedamericanomall.common.utils.RateLimitUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * 限流切面 / 工具类的 {@code @ConditionalOnClass} 守卫验证（R-1 遗留项回归测试）。
 *
 * <p>为什么必须成对守卫：{@link RateLimitAspect} 强依赖 {@link RateLimitUtils}。
 * 若只守卫其中一个，无 Redis 服务会从「类找不到」变成「Bean 找不到」
 * （{@code NoSuchBeanDefinitionException}），故障只是换了张脸。
 * 本测试同时断言两者缺席，锁死「要么都在要么都不在」的约束。
 *
 * <p>用 {@link WebApplicationContextRunner} 是因为两者都标注了
 * {@code @ConditionalOnWebApplication(SERVLET)}，非 Web 上下文下条件本就不匹配，
 * 测不出类路径守卫是否生效。
 */
@DisplayName("条件装配 — 限流组件的 Redis 类路径守卫")
class RateLimitConditionGuardTest {

    private static final Class<?>[] REDIS_CLASSES = {StringRedisTemplate.class, RedisScript.class};

    @Test
    @DisplayName("Web 应用 + 类路径无 Redis → RateLimitUtils 与 RateLimitAspect 双双缺席且启动成功")
    void shouldSkipRateLimitBeans_whenRedisClassesAbsent() {
        new WebApplicationContextRunner()
                .withClassLoader(new FilteredClassLoader(REDIS_CLASSES))
                .withUserConfiguration(RateLimitUtils.class, RateLimitAspect.class)
                .run(context -> {
                    assertThat(context.getStartupFailure())
                            .as("无 Redis 的 Web 服务必须能正常启动").isNull();
                    assertThat(context.getBeanNamesForType(RateLimitUtils.class))
                            .as("RateLimitUtils 应被类路径守卫跳过").isEmpty();
                    assertThat(context.getBeanNamesForType(RateLimitAspect.class))
                            .as("RateLimitAspect 必须与 RateLimitUtils 同时缺席").isEmpty();
                });
    }

    @Test
    @DisplayName("Web 应用 + 类路径有 Redis → 两个 Bean 均注册（守卫未过度收紧）")
    void shouldRegisterRateLimitBeans_whenRedisClassesPresent() {
        new WebApplicationContextRunner()
                .withUserConfiguration(RateLimitUtils.class, RateLimitAspect.class)
                .withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class))
                .withBean("rateLimitRedisScript", RedisScript.class, () -> mock(RedisScript.class))
                .run(context -> {
                    assertThat(context.getStartupFailure()).isNull();
                    assertThat(context.getBeanNamesForType(RateLimitUtils.class)).hasSize(1);
                    assertThat(context.getBeanNamesForType(RateLimitAspect.class)).hasSize(1);
                });
    }
}
