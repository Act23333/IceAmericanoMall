package org.icedamericanomall.marketing.domain.port;

import java.time.Duration;

/**
 * 普通券领取去重能力（领域层定义）—— 基础设施层用 Redis SET + Lua 实现
 * （{@code infrastructure.adapter.redis.RedisCouponClaimDedupStore}）。
 *
 * <p>所有券类型领取前先过 Redis 去重保护 DB（DB 侧仍有唯一持券兜底）。
 */
public interface CouponClaimDedupStore {

    /**
     * 尝试去重（放行本次领取）。
     *
     * @param couponId 优惠券模板主键 ID
     * @param userId   用户 ID
     * @param ttl      去重集合 TTL（与活动结束时间对齐，兜底 7 天）
     * @return 1=首次(放行), 0=已领取(拒绝)
     */
    long tryDedup(Long couponId, Long userId, Duration ttl);
}
