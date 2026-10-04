package org.icedamericanomall.marketing.domain.port;

import java.time.Duration;

/**
 * 秒杀级优惠券领取存储能力（领域层定义）—— 基础设施层用 Redis Lua 原子预扣实现
 * （{@code infrastructure.adapter.redis.RedisCouponGrabStore}）。
 *
 * <p>适用于 grabType=NEED_GRAB 的券：Redis 预扣成功后 DB 落库失败必须
 * {@link #rollbackClaim(Long, Long)} 归还名额，否则「Redis 扣了、DB 没有」导致名额永久缺失。
 */
public interface CouponGrabStore {

    /** 返回值：库存耗尽。 */
    long SOLD_OUT = -1L;

    /** 返回值：同一用户重复领取。 */
    long DUPLICATE = -2L;

    /**
     * 尝试领取秒杀级优惠券。
     *
     * @param couponId 优惠券模板主键 ID
     * @param userId   用户 ID
     * @param ttl      去重集合 TTL（与活动结束时间对齐，兜底 7 天）
     * @return &gt;=0 剩余库存, {@link #SOLD_OUT} 已抢光, {@link #DUPLICATE} 已领取过
     */
    long tryClaim(Long couponId, Long userId, Duration ttl);

    /**
     * 补偿通道：预扣成功后 DB 落库失败时回滚 Redis（INCR 库存 + SREM 用户）。
     *
     * @param couponId 优惠券模板主键 ID
     * @param userId   用户 ID
     */
    void rollbackClaim(Long couponId, Long userId);

    /** 预热秒杀券库存到 Redis（创建 NEED_GRAB 类型优惠券时调用）。 */
    void preloadStock(Long couponId, int totalQty, Duration ttl);

    /** 查询 Redis 剩余库存（管理后台用）。 */
    long getRemainingStock(Long couponId);
}
