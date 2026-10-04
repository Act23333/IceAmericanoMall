package org.icedamericanomall.common.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.scripting.support.ResourceScriptSource;

/**
 * 限流 Lua 脚本注册表 —— 把 {@code classpath:lua/rate_limit.lua} 加载为单例
 * {@link RedisScript} Bean，供 {@link org.icedamericanomall.common.utils.RateLimitUtils} 使用。
 *
 * <p>{@code @ConditionalOnClass} 守卫（V5.2 补齐，R-1 遗留项）：
 * ia-common 对 Redis 的依赖是 optional，未引入 Redis 的服务类路径上没有
 * {@link RedisScript}。此前本类无条件加载 {@code classpath:lua/*.lua}，
 * 会在无 Redis 的服务里注册无用 Bean；守卫后整体跳过。
 *
 * <p><b>V5.3 清理</b>：{@code del_redisKey.lua / check_limit.lua / login_rate_limit.lua}
 * 及其脚本 Bean 已删除 —— 全仓库零调用（删除键的路径走
 * {@code StringRedisTemplate#delete}，登录限流走 {@code @RateLimit} 的
 * {@code rate_limit.lua}），留在内核里只会误导后来者。
 */
@Configuration
@ConditionalOnClass(RedisScript.class)
public class RedisLuaScriptConfig {

    /** 公共限流脚本（全局单例，复用）。 */
    @Bean
    public RedisScript<Long> rateLimitRedisScript() {
        DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>();
        // ClassPathResource 不解析 "classpath:" 前缀，路径必须是 "lua/rate_limit.lua"
        redisScript.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/rate_limit.lua")));
        // 设置返回值类型：脚本返回1/0，对应Long
        redisScript.setResultType(Long.class);
        return redisScript;
    }
}
