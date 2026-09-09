package org.icedamericanomall.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * V4.5: 优惠券领取 Redis 去重 Lua 脚本（京东标准：所有券都走 Redis 去重，保护 DB）。
 * <p>
 * 与 {@link CouponGrabLuaScript}（秒杀券：去重+库存扣减）不同，本脚本仅做去重，
 * 适用于 NORMAL grabType 的普通券（不限量/限量均适用）。
 * <p>
 * KEYS[1] = coupon:claimed:{couponId} (SET)
 * ARGV[1] = userId
 * <p>
 * 返回值: 1=首次领取(放行到DB), 0=已领取过(拒绝)
 */
@Component
@RequiredArgsConstructor
public class CouponClaimLuaScript {

    private final RedisTemplate<String, String> redisTemplate;

    private static final String LUA = """
            local claimed = redis.call('SISMEMBER', KEYS[1], ARGV[1])
            if claimed == 1 then return 0 end
            redis.call('SADD', KEYS[1], ARGV[1])
            return 1
            """;

    private final DefaultRedisScript<Long> script = new DefaultRedisScript<>(LUA, Long.class);

    /**
     * V4.5: 尝试 Redis 去重。
     * @param couponId 优惠券模板主键ID
     * @param userId   用户ID
     * @return 1=首次(放行), 0=已领取(拒绝)
     */
    public long tryDedup(Long couponId, Long userId) {
        String claimedKey = "coupon:claimed:" + couponId;
        Long result = redisTemplate.execute(script,
                List.of(claimedKey), String.valueOf(userId));
        return result != null ? result : 0;
    }
}
