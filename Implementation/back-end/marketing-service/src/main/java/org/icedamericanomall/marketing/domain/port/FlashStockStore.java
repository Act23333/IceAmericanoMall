package org.icedamericanomall.marketing.domain.port;

import java.time.Duration;

/**
 * 秒杀库存存储能力（领域层定义）—— 基础设施层用 Redis 分片 + Lua 原子预扣实现
 * （{@code infrastructure.adapter.redis.RedisFlashStockStore}）。
 *
 * <p>领域只表达秒杀库存语义（预扣/限购/预热/查询），不感知 Lua 与 Key 布局。
 */
public interface FlashStockStore {

    /** 预扣返回值：全部分片售罄。 */
    long SOLD_OUT = -1L;

    /** 预扣返回值：已达用户限购上限。 */
    long LIMIT_EXCEEDED = -2L;

    /**
     * 热点分片预扣（含惰性兜底）。
     *
     * @param flashId        秒杀活动 ID
     * @param userId         用户 ID
     * @param limit          用户限购数
     * @param remainingStock DB 口径剩余库存（用于惰性初始化，&lt;=0 时跳过初始化）
     * @param ttl            库存键 / 限购计数键 TTL（与活动结束时间对齐，兜底 7 天）
     * @return &gt;=0 剩余库存, {@link #SOLD_OUT} 售罄, {@link #LIMIT_EXCEEDED} 超限购
     */
    long tryDeduct(Long flashId, Long userId, int limit, int remainingStock, Duration ttl);

    /**
     * 显式预热库存：平均分配到各分片并写哨兵（覆盖写）。
     *
     * @param flashId    秒杀活动 ID
     * @param totalStock 全量库存
     * @param ttl        键 TTL（与活动结束时间对齐）
     */
    void preloadStock(Long flashId, int totalStock, Duration ttl);

    /** 显式预热库存（默认 TTL：与活动结束时间无关时兜底 7 天）。 */
    void preloadStock(Long flashId, int totalStock);

    /** 查询总剩余库存（管理端/巡检用）。 */
    long getTotalStock(Long flashId);
}
