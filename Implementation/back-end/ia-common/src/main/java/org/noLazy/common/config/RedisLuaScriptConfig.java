package org.noLazy.common.config;


import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import jakarta.annotation.Resource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.scripting.support.ResourceScriptSource;

@Configuration
public class RedisLuaScriptConfig {

    @Resource
    private ResourceLoader resourceLoader;
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
        redisScript.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/rate_limit.lua")));
        // 设置返回值类型：脚本返回1/0，对应Long
        redisScript.setResultType(Long.class);
        return redisScript;
    }

    @Bean
    public RedisScript<Long> checkLimitScript() {
        DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>();
        redisScript.setScriptSource(new ResourceScriptSource(resourceLoader.getResource("classpath:lua/check_limit.lua")));
        redisScript.setResultType(Long.class);
        return redisScript;
    }

    @Bean
    public RedisScript<Void> loginRateLimitRedisScript() {
        DefaultRedisScript<Void> redisScript = new DefaultRedisScript<>();
        redisScript.setScriptSource(new ResourceScriptSource(resourceLoader.getResource("classpath:lua/login_rate_limit.lua")));
        redisScript.setResultType(Void.class);
        return redisScript;
    }
}