package org.icedamericanomall.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * V3.7: 秒杀库存 Lua 脚本 — 热点Key分片 + 用户限购（大厂标准：JD 秒杀漏斗模型）。
 * <p>
 * 库存拆为 SHARDS 片: flash:stock:{flashId}:0 ~ :{N-1}，随机选片消除单key热点。
 * V3.6 单 key → V3.7 分片: 并发提升 SHARDS 倍。
 */
@Component
@RequiredArgsConstructor
public class FlashSaleLuaScript {

    private final RedisTemplate<String, String> redisTemplate;
    private static final int SHARDS = 10;

    /** Lua: KEYS[1]=分片库存key, KEYS[2]=用户购买记录, ARGV[1]=限购数 */
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
     * V3.7: 热点分片预扣。
     * 随机选分片→尝试扣减→失败则轮询下一片→全部耗尽返回-1。
     */
    public long tryDeduct(Long flashId, Long userId, int limit) {
        String userKey = "flash:user:" + flashId + ":" + userId;
        int startShard = ThreadLocalRandom.current().nextInt(SHARDS);

        for (int i = 0; i < SHARDS; i++) {
            int shard = (startShard + i) % SHARDS;
            String stockKey = "flash:stock:" + flashId + ":" + shard;
            Long result = redisTemplate.execute(script,
                    Arrays.asList(stockKey, userKey), String.valueOf(limit));
            if (result != null && result >= 0) return result;
            if (result != null && result == -2) return -2; // 已达限购, 不用重试
        }
        return -1; // 所有分片售罄
    }

    /** 预热库存: 平均分配到各分片 */
    public void preloadStock(Long flashId, int totalStock) {
        int perShard = totalStock / SHARDS;
        int remainder = totalStock % SHARDS;
        for (int i = 0; i < SHARDS; i++) {
            int stock = perShard + (i < remainder ? 1 : 0);
            redisTemplate.opsForValue().set("flash:stock:" + flashId + ":" + i, String.valueOf(stock));
        }
    }

    /** 查询总剩余库存 */
    public long getTotalStock(Long flashId) {
        long total = 0;
        for (int i = 0; i < SHARDS; i++) {
            String v = redisTemplate.opsForValue().get("flash:stock:" + flashId + ":" + i);
            if (v != null) total += Long.parseLong(v);
        }
        return total;
    }
}
