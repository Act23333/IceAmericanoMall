package org.icedamericanomall.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * V4.0: 秒杀级优惠券领取 Lua 脚本（京东标准：Redis 原子预扣 + 用户去重）。
 * <p>
 * 复用 {@link FlashSaleLuaScript} 的 Lua 原子操作模式，适配优惠券领取场景：
 * KEYS[1] = coupon:grab:stock:{couponId}   (库存计数)
 * KEYS[2] = coupon:grab:claimed:{couponId} (已领取用户 SET)
 * ARGV[1] = userId
 * <p>
 * 返回值: >=0 剩余库存, -1 已抢光, -2 已领取过
 */
@Component
@RequiredArgsConstructor
public class CouponGrabLuaScript {

    private final RedisTemplate<String, String> redisTemplate;

    private static final String LUA = """
            local stock = tonumber(redis.call('GET', KEYS[1]) or '0')
            if stock <= 0 then return -1 end
            local claimed = redis.call('SISMEMBER', KEYS[2], ARGV[1])
            if claimed == 1 then return -2 end
            redis.call('DECR', KEYS[1])
            redis.call('SADD', KEYS[2], ARGV[1])
            return stock - 1
            """;

    private final DefaultRedisScript<Long> script = new DefaultRedisScript<>(LUA, Long.class);

    /**
     * V4.0: 尝试领取秒杀级优惠券。
     * @param couponId 优惠券模板主键ID
     * @param userId   用户ID
     * @return >=0 剩余库存, -1 已抢光, -2 已领取过
     */
    public long tryClaim(Long couponId, Long userId) {
        String stockKey = "coupon:grab:stock:" + couponId;
        String claimedKey = "coupon:grab:claimed:" + couponId;
        Long result = redisTemplate.execute(script,
                List.of(stockKey, claimedKey), String.valueOf(userId));
        return result != null ? result : -1;
    }

    /** V4.0: 预热秒杀券库存到 Redis（创建 NEED_GRAB 类型优惠券时自动调用）。 */
    public void preloadStock(Long couponId, int totalQty) {
        redisTemplate.opsForValue().set("coupon:grab:stock:" + couponId, String.valueOf(totalQty));
    }

    /** 查询 Redis 剩余库存（管理后台用）。 */
    public long getRemainingStock(Long couponId) {
        String v = redisTemplate.opsForValue().get("coupon:grab:stock:" + couponId);
        return v != null ? Long.parseLong(v) : 0;
    }
}
