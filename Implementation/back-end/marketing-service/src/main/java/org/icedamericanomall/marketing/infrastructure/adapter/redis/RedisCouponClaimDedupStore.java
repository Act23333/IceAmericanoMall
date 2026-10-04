package org.icedamericanomall.marketing.infrastructure.adapter.redis;

import lombok.RequiredArgsConstructor;
import org.icedamericanomall.marketing.domain.port.CouponClaimDedupStore;
import org.icedamericanomall.marketing.infrastructure.constant.MarketingRedisKeys;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * V4.5: 优惠券领取 Redis 去重 Lua 脚本（京东标准：所有券都走 Redis 去重，保护 DB）。
 *
 * <p>实现领域端口 {@link CouponClaimDedupStore}。与秒杀券通道（去重 + 库存扣减，
 * 见 {@code RedisCouponGrabStore}）不同，本类仅做去重，适用于 NORMAL grabType 的
 * 普通券（不限量/限量均适用）。
 *
 * <p>KEYS[1] = {@code coupon:claimed:{couponId}} (SET)，ARGV[1] = userId，ARGV[2] = TTL（秒）。
 * 返回值: 1=首次领取(放行到 DB), 0=已领取过(拒绝)。
 */
@Component
@RequiredArgsConstructor
public class RedisCouponClaimDedupStore implements CouponClaimDedupStore {

    private final RedisTemplate<String, String> redisTemplate;

    private static final String LUA = """
            local claimed = redis.call('SISMEMBER', KEYS[1], ARGV[1])
            if claimed == 1 then return 0 end
            redis.call('SADD', KEYS[1], ARGV[1])
            redis.call('EXPIRE', KEYS[1], ARGV[2])
            return 1
            """;

    private final DefaultRedisScript<Long> script = new DefaultRedisScript<>(LUA, Long.class);

    /**
     * V4.5: 尝试 Redis 去重。
     *
     * @param couponId 优惠券模板主键 ID
     * @param userId   用户 ID
     * @param ttl      去重集合 TTL（与活动结束时间对齐，兜底 7 天；每次新增成员顺延，上限即活动结束后 1 天）
     * @return 1=首次(放行), 0=已领取(拒绝)
     */
    public long tryDedup(Long couponId, Long userId, Duration ttl) {
        String claimedKey = MarketingRedisKeys.couponClaimed(couponId);
        Long result = redisTemplate.execute(script, List.of(claimedKey),
                String.valueOf(userId), String.valueOf(ttl.getSeconds()));
        return result != null ? result : 0;
    }
}
