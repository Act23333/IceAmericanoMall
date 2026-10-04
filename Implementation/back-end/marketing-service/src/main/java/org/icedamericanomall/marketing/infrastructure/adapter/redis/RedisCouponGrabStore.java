package org.icedamericanomall.marketing.infrastructure.adapter.redis;

import lombok.RequiredArgsConstructor;
import org.icedamericanomall.marketing.domain.port.CouponGrabStore;
import org.icedamericanomall.marketing.infrastructure.constant.MarketingRedisKeys;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * V4.0: 秒杀级优惠券领取 Lua 脚本（京东标准：Redis 原子预扣 + 用户去重）。
 *
 * <p>实现领域端口 {@link CouponGrabStore}。复用秒杀库存的 Lua 原子操作模式，适配优惠券领取场景：
 * KEYS[1] = {@code coupon:grab:stock:{couponId}}   (库存计数)
 * KEYS[2] = {@code coupon:grab:claimed:{couponId}} (已领取用户 SET)
 * ARGV[1] = userId, ARGV[2] = 去重集合 TTL（秒）
 *
 * <p>返回值: &gt;=0 剩余库存, {@link #SOLD_OUT} 已抢光, {@link #DUPLICATE} 已领取过。
 * 键布局统一由 {@link MarketingRedisKeys} 构造，禁止内联拼接。
 */
@Component
@RequiredArgsConstructor
public class RedisCouponGrabStore implements CouponGrabStore {

    private final RedisTemplate<String, String> redisTemplate;

    private static final String CLAIM_LUA = """
            local stock = tonumber(redis.call('GET', KEYS[1]) or '0')
            if stock <= 0 then return -1 end
            local claimed = redis.call('SISMEMBER', KEYS[2], ARGV[1])
            if claimed == 1 then return -2 end
            redis.call('DECR', KEYS[1])
            redis.call('SADD', KEYS[2], ARGV[1])
            redis.call('EXPIRE', KEYS[2], ARGV[2])
            return stock - 1
            """;

    /** 补偿：INCR 回滚一次预扣库存 + SREM 撤销去重标记（DB 落库失败时调用）。 */
    private static final String ROLLBACK_LUA = """
            redis.call('INCR', KEYS[1])
            redis.call('SREM', KEYS[2], ARGV[1])
            return 1
            """;

    private final DefaultRedisScript<Long> claimScript = new DefaultRedisScript<>(CLAIM_LUA, Long.class);

    private final DefaultRedisScript<Long> rollbackScript = new DefaultRedisScript<>(ROLLBACK_LUA, Long.class);

    /**
     * V4.0: 尝试领取秒杀级优惠券。
     *
     * @param couponId 优惠券模板主键 ID
     * @param userId   用户 ID
     * @param ttl      去重集合 TTL（与活动结束时间对齐，兜底 7 天）
     * @return &gt;=0 剩余库存, {@link #SOLD_OUT} 已抢光, {@link #DUPLICATE} 已领取过
     */
    public long tryClaim(Long couponId, Long userId, Duration ttl) {
        List<String> keys = List.of(MarketingRedisKeys.couponGrabStock(couponId),
                MarketingRedisKeys.couponGrabClaimed(couponId));
        Long result = redisTemplate.execute(claimScript, keys,
                String.valueOf(userId), String.valueOf(ttl.getSeconds()));
        return result != null ? result : SOLD_OUT;
    }

    /**
     * V4.0 补偿通道：预扣成功后 DB 落库失败时回滚 Redis（INCR 库存 + SREM 用户），
     * 否则该名额会永久缺失（Redis 扣了、DB 没有）。
     *
     * @param couponId 优惠券模板主键 ID
     * @param userId   用户 ID
     */
    public void rollbackClaim(Long couponId, Long userId) {
        List<String> keys = List.of(MarketingRedisKeys.couponGrabStock(couponId),
                MarketingRedisKeys.couponGrabClaimed(couponId));
        redisTemplate.execute(rollbackScript, keys, String.valueOf(userId));
    }

    /** V4.0: 预热秒杀券库存到 Redis（创建 NEED_GRAB 类型优惠券时自动调用）。 */
    public void preloadStock(Long couponId, int totalQty, Duration ttl) {
        redisTemplate.opsForValue().set(MarketingRedisKeys.couponGrabStock(couponId),
                String.valueOf(totalQty), ttl);
    }

    /** 查询 Redis 剩余库存（管理后台用）。 */
    public long getRemainingStock(Long couponId) {
        String v = redisTemplate.opsForValue().get(MarketingRedisKeys.couponGrabStock(couponId));
        return v != null ? Long.parseLong(v) : 0;
    }
}
