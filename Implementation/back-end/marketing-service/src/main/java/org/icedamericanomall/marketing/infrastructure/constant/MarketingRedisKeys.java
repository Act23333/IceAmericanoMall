package org.icedamericanomall.marketing.infrastructure.constant;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 营销域 Redis Key 统一常量（本服务独占，禁止跨服务共享）。
 *
 * <p>本服务所有 Redis Key 一律经本类构造，业务代码内不得再出现内联拼接的 key 字面量
 * （见 09-constraints：魔法值必须提常量）。
 *
 * <pre>
 * flash:stock:{flashId}:{shard}    秒杀分片库存（热点分片，SHARDS 片）
 * flash:stock-init:{flashId}       秒杀库存初始化标记（惰性兜底幂等哨兵）
 * flash:user:{flashId}:{userId}    秒杀用户购买计数（限购）
 * coupon:grab:stock:{couponId}     秒杀券库存
 * coupon:grab:claimed:{couponId}   秒杀券已领取用户集合
 * coupon:claimed:{couponId}        普通券领取去重集合
 * user:sign:{userId}:{yyyy:MM}     签到 Bitmap（1 位/天）
 * </pre>
 */
public final class MarketingRedisKeys {

    /** 秒杀库存分片前缀。 */
    public static final String FLASH_STOCK_PREFIX = "flash:stock:";

    /** 秒杀库存初始化标记前缀（惰性兜底哨兵，与分片库存同生命周期）。 */
    public static final String FLASH_STOCK_INIT_PREFIX = "flash:stock-init:";

    /** 秒杀用户限购计数前缀。 */
    public static final String FLASH_USER_PREFIX = "flash:user:";

    /** 秒杀券库存前缀。 */
    public static final String COUPON_GRAB_STOCK_PREFIX = "coupon:grab:stock:";

    /** 秒杀券已领取用户集合前缀。 */
    public static final String COUPON_GRAB_CLAIMED_PREFIX = "coupon:grab:claimed:";

    /** 普通优惠券领取去重集合前缀。 */
    public static final String COUPON_CLAIMED_PREFIX = "coupon:claimed:";

    /** 签到 Bitmap 前缀。 */
    public static final String USER_SIGN_PREFIX = "user:sign:";

    /** 键 TTL 兜底值：无法对齐活动结束时间（缺失/已过期）时使用 7 天。 */
    public static final Duration DEFAULT_TTL = Duration.ofDays(7);

    /** 活动结束后的额外冗余时长：避免活动刚结束即有请求落到已失效键上。 */
    private static final Duration EXPIRE_BUFFER = Duration.ofDays(1);

    private MarketingRedisKeys() {
    }

    /** 秒杀分片库存 key：{@code flash:stock:{flashId}:{shard}}。 */
    public static String flashStockShard(Long flashId, int shard) {
        return FLASH_STOCK_PREFIX + flashId + ":" + shard;
    }

    /** 秒杀库存初始化标记 key：{@code flash:stock-init:{flashId}}。 */
    public static String flashStockInit(Long flashId) {
        return FLASH_STOCK_INIT_PREFIX + flashId;
    }

    /** 秒杀用户限购计数 key：{@code flash:user:{flashId}:{userId}}。 */
    public static String flashUser(Long flashId, Long userId) {
        return FLASH_USER_PREFIX + flashId + ":" + userId;
    }

    /** 秒杀券库存 key：{@code coupon:grab:stock:{couponId}}。 */
    public static String couponGrabStock(Long couponId) {
        return COUPON_GRAB_STOCK_PREFIX + couponId;
    }

    /** 秒杀券已领取用户集合 key：{@code coupon:grab:claimed:{couponId}}。 */
    public static String couponGrabClaimed(Long couponId) {
        return COUPON_GRAB_CLAIMED_PREFIX + couponId;
    }

    /** 普通券领取去重集合 key：{@code coupon:claimed:{couponId}}。 */
    public static String couponClaimed(Long couponId) {
        return COUPON_CLAIMED_PREFIX + couponId;
    }

    /** 签到 Bitmap key：{@code user:sign:{userId}:{yyyy:MM}}（布局自 user-service 迁入后保持不变）。 */
    public static String userSign(Long userId, String yearMonth) {
        return USER_SIGN_PREFIX + userId + ":" + yearMonth;
    }

    /**
     * 计算与活动结束时间对齐的 TTL。
     *
     * @param endTime 活动结束时间（可为空）
     * @return 结束时间 +1 天冗余；若为空/已过期则兜底 {@link #DEFAULT_TTL}（7 天）
     */
    public static Duration ttlUntil(LocalDateTime endTime) {
        if (endTime == null) {
            return DEFAULT_TTL;
        }
        Duration until = Duration.between(LocalDateTime.now(), endTime).plus(EXPIRE_BUFFER);
        return until.compareTo(DEFAULT_TTL) > 0 ? until : DEFAULT_TTL;
    }
}
