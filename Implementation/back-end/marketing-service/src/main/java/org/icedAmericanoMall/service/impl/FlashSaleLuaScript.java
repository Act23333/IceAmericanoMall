package org.icedAmericanoMall.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * V3.6: 秒杀库存 Lua 脚本（大厂标准：Redis 原子预扣 + 用户限购）。
 * <p>
 * 单次 Redis 调用完成: 库存检查→用户限购→扣库存→记用户。
 * 原子性保证: Lua 脚本在 Redis 单线程内执行，无竞态。
 */
@Component
@RequiredArgsConstructor
public class FlashSaleLuaScript {

    private final RedisTemplate<String, String> redisTemplate;

    /** Lua 脚本: KEYS[1]=库存key, KEYS[2]=用户购买记录, ARGV[1]=限购数 */
    private static final String LUA = """
            local stock = tonumber(redis.call('GET', KEYS[1]) or '0')
            if stock <= 0 then return -1 end
            local bought = tonumber(redis.call('GET', KEYS[2]) or '0')
            local limit = tonumber(ARGV[1])
            if bought >= limit then return -2 end
            redis.call('DECR', KEYS[1])
            redis.call('INCR', KEYS[2])
            return stock - 1
            """;

    private final DefaultRedisScript<Long> script = new DefaultRedisScript<>(LUA, Long.class);

    /**
     * 执行秒杀预扣。
     * @param flashId 秒杀活动ID
     * @param userId  用户ID
     * @param limit   每人限购数量
     * @return >0 剩余库存, -1 已售罄, -2 已达限购
     */
    public long tryDeduct(Long flashId, Long userId, int limit) {
        List<String> keys = Arrays.asList(
                "flash:stock:" + flashId,
                "flash:user:" + flashId + ":" + userId);
        Long result = redisTemplate.execute(script, keys, String.valueOf(limit));
        return result != null ? result : -1;
    }

    /** 秒杀前预热库存到 Redis */
    public void preloadStock(Long flashId, int stock) {
        redisTemplate.opsForValue().set("flash:stock:" + flashId, String.valueOf(stock));
    }
}
