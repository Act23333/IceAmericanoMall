package org.icedamericanomall.common.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.scripting.support.ResourceScriptSource;

/**
 * 限流 / 去重 Lua 脚本注册表 —— 把 {@code classpath:lua/*.lua} 加载为单例
 * {@link RedisScript} Bean，供 {@code RateLimitUtils} 等组件复用。
 *
 * <p>{@code @ConditionalOnClass} 守卫（V5.2 补齐，R-1 遗留项）：
 * ia-common 对 Redis 的依赖是 optional，未引入 Redis 的服务类路径上没有
 * {@link RedisScript}。此前本类无条件加载 {@code classpath:lua/*.lua}，
 * 会在无 Redis 的服务里注册 4 个无用 Bean；守卫后整体跳过。
 *
 * <p>{@code ResourceLoader} 改为<b>构造器注入</b>（V5.2）：
 * 原先是 {@code @Resource} 字段注入，违反项目「依赖注入一律使用构造器注入」的硬约束。
 */
@Configuration
@ConditionalOnClass(RedisScript.class)
public class RedisLuaScriptConfig {

    private final ResourceLoader resourceLoader;

    public RedisLuaScriptConfig(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    /**
     * 公共删除Key脚本（全局单例，复用）
     */
    @Bean
    public RedisScript<Long> deleteRedisKeyRedisScript() {
        DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>();
        // 指定lua脚本路径
        redisScript.setScriptSource(new ResourceScriptSource(resourceLoader.getResource("classpath:lua/del_redisKey.lua")));
        // 设置返回值类型：脚本返回1/0，对应Long
        redisScript.setResultType(Long.class);
        return redisScript;
    }

    /**
     * 公共限流脚本（全局单例，复用）
     */
    @Bean
    public RedisScript<Long> rateLimitRedisScript() {
        DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>();
        // 指定lua脚本路径
        //        //ClassPathResource 不会解析 classpath:，所以它直接去找名为 classpath:lua/rate_limit.lua 的文件。去掉多余的 classpath: → 变成 "lua/rate_limit.lua" → 找到文件 → @RateLimit 正常工作了
        redisScript.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/rate_limit.lua")));
        // 设置返回值类型：脚本返回1/0，对应Long
        redisScript.setResultType(Long.class);
        return redisScript;
    }

    @Bean
    public RedisScript<Long> checkLimitScript() {
        DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>();
        redisScript.setScriptSource(new ResourceScriptSource(resourceLoader.getResource("lua/check_limit.lua")));
        redisScript.setResultType(Long.class);
        return redisScript;
    }

    @Bean
    public RedisScript<Void> loginRateLimitRedisScript() {
        DefaultRedisScript<Void> redisScript = new DefaultRedisScript<>();
        redisScript.setScriptSource(new ResourceScriptSource(resourceLoader.getResource("lua/login_rate_limit.lua")));
        redisScript.setResultType(Void.class);
        return redisScript;
    }
}
