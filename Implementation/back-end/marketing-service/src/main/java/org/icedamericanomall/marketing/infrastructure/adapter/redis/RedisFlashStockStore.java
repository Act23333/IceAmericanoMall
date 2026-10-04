package org.icedamericanomall.marketing.infrastructure.adapter.redis;

import lombok.RequiredArgsConstructor;
import org.icedamericanomall.marketing.domain.port.FlashStockStore;
import org.icedamericanomall.marketing.infrastructure.constant.MarketingRedisKeys;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * V3.7: 秒杀库存 Lua 脚本 — 热点 Key 分片 + 用户限购（大厂标准：JD 秒杀漏斗模型）。
 *
 * <p>库存拆为 {@code SHARDS} 片：{@code flash:stock:{flashId}:0 ~ :N-1}，随机选片消除单 key 热点，
 * 并发能力提升 SHARDS 倍。所有 key 均经 {@link MarketingRedisKeys} 构造。
 *
 * <p>实现领域端口 {@link FlashStockStore}（预扣/限购/预热/查询语义在领域层，Lua 与 Key 布局在本类）。
 *
 * <p><b>库存预热双保险</b>：
 * <ol>
 *   <li>显式预热 {@link #preloadStock(Long, int, Duration)}：活动状态流转到「进行中」时调用，
 *       把 DB 全量库存平均分配到各分片（当前无管理端状态流转端点，暂由第 2 条兜底）。</li>
 *   <li>惰性兜底 {@link #ensureStockInitialized}：每次预扣前用带哨兵的 Lua 原子检查，
 *       分片与哨兵同时缺失（首次上线 / Redis 重启丢键）时按 DB 剩余库存重建分片，
 *       重建后写入哨兵，后续请求不再重建 —— 既不会把已扣减的库存重置回全量，
 *       也不会让未预热的场次永远命中「售罄」。</li>
 * </ol>
 */
@Component
@RequiredArgsConstructor
public class RedisFlashStockStore implements FlashStockStore {

    /** 库存分片数。 */
    private static final int SHARDS = 10;

    /** 初始化哨兵值。 */
    private static final String INIT_FLAG = "1";

    /**
     * Lua: KEYS[1]=分片库存key, KEYS[2]=用户购买记录, ARGV[1]=限购数, ARGV[2]=TTL 秒（限购记录随活动结束过期）
     */
    private static final String DEDUCT_LUA = """
            local stock = tonumber(redis.call('GET', KEYS[1]) or '0')
            if stock <= 0 then return -1 end
            local bought = tonumber(redis.call('GET', KEYS[2]) or '0')
            local limit = tonumber(ARGV[1])
            if bought >= limit then return -2 end
            redis.call('DECR', KEYS[1])
            redis.call('INCR', KEYS[2])
            redis.call('EXPIRE', KEYS[2], ARGV[2])
            return stock - 1
            """;

    /**
     * 惰性初始化 Lua（原子）:
     * KEYS[1..N-1]=分片库存key, KEYS[N]=初始化哨兵key
     * ARGV[1]=每片基础库存, ARGV[2]=余数（前 remainder 片各 +1）, ARGV[3]=TTL 秒
     * 返回 1=本次初始化，0=已初始化（哨兵存在，直接返回不覆盖）。
     */
    private static final String INIT_LUA = """
            if redis.call('EXISTS', KEYS[#KEYS]) == 1 then return 0 end
            local perShard = tonumber(ARGV[1])
            local remainder = tonumber(ARGV[2])
            local ttl = tonumber(ARGV[3])
            for i = 1, #KEYS - 1 do
              local stock = perShard
              if i <= remainder then stock = stock + 1 end
              redis.call('SET', KEYS[i], tostring(stock), 'EX', ttl)
            end
            redis.call('SET', KEYS[#KEYS], '1', 'EX', ttl)
            return 1
            """;

    private final RedisTemplate<String, String> redisTemplate;

    private final DefaultRedisScript<Long> deductScript = new DefaultRedisScript<>(DEDUCT_LUA, Long.class);

    private final DefaultRedisScript<Long> initScript = new DefaultRedisScript<>(INIT_LUA, Long.class);

    /**
     * 热点分片预扣（含惰性兜底）。
     *
     * @param flashId       秒杀活动 ID
     * @param userId        用户 ID
     * @param limit         用户限购数
     * @param remainingStock DB 口径剩余库存（用于惰性初始化，&lt;=0 时跳过初始化）
     * @param ttl           库存键 / 限购计数键 TTL（与活动结束时间对齐，兜底 7 天）
     * @return &gt;=0 剩余库存, {@link #SOLD_OUT} 售罄, {@link #LIMIT_EXCEEDED} 超限购
     */
    public long tryDeduct(Long flashId, Long userId, int limit, int remainingStock, Duration ttl) {
        ensureStockInitialized(flashId, remainingStock, ttl);

        String userKey = MarketingRedisKeys.flashUser(flashId, userId);
        int startShard = ThreadLocalRandom.current().nextInt(SHARDS);
        String ttlArg = String.valueOf(ttlSeconds(ttl));

        for (int i = 0; i < SHARDS; i++) {
            int shard = (startShard + i) % SHARDS;
            String stockKey = MarketingRedisKeys.flashStockShard(flashId, shard);
            Long result = redisTemplate.execute(deductScript,
                    Arrays.asList(stockKey, userKey), String.valueOf(limit), ttlArg);
            if (result != null && result >= 0) {
                return result;
            }
            if (result != null && result == LIMIT_EXCEEDED) {
                return LIMIT_EXCEEDED; // 已达限购, 不用重试
            }
        }
        return SOLD_OUT; // 所有分片售罄
    }

    /**
     * 显式预热库存：平均分配到各分片并写哨兵（覆盖写，供活动状态流转到「进行中」时调用）。
     *
     * @param flashId    秒杀活动 ID
     * @param totalStock 全量库存
     * @param ttl        键 TTL（与活动结束时间对齐）
     */
    public void preloadStock(Long flashId, int totalStock, Duration ttl) {
        long ttlSeconds = ttlSeconds(ttl);
        int perShard = totalStock / SHARDS;
        int remainder = totalStock % SHARDS;
        for (int i = 0; i < SHARDS; i++) {
            int stock = perShard + (i < remainder ? 1 : 0);
            redisTemplate.opsForValue().set(MarketingRedisKeys.flashStockShard(flashId, i),
                    String.valueOf(stock), Duration.ofSeconds(ttlSeconds));
        }
        redisTemplate.opsForValue().set(MarketingRedisKeys.flashStockInit(flashId), INIT_FLAG,
                Duration.ofSeconds(ttlSeconds));
    }

    /** 显式预热库存（默认 TTL：与活动结束时间无关时兜底 7 天）。 */
    public void preloadStock(Long flashId, int totalStock) {
        preloadStock(flashId, totalStock, MarketingRedisKeys.DEFAULT_TTL);
    }

    /** 查询总剩余库存（管理端/巡检用）。 */
    public long getTotalStock(Long flashId) {
        long total = 0;
        for (int i = 0; i < SHARDS; i++) {
            String v = redisTemplate.opsForValue().get(MarketingRedisKeys.flashStockShard(flashId, i));
            if (v != null) {
                total += Long.parseLong(v);
            }
        }
        return total;
    }

    /**
     * 惰性兜底：分片与哨兵都不存在时，按 DB 剩余库存原子重建分片（哨兵防止重复重置）。
     */
    private void ensureStockInitialized(Long flashId, int remainingStock, Duration ttl) {
        if (remainingStock <= 0) {
            return; // DB 已无可售库存，无需初始化（预扣自然返回售罄）
        }
        List<String> keys = new ArrayList<>(SHARDS + 1);
        for (int i = 0; i < SHARDS; i++) {
            keys.add(MarketingRedisKeys.flashStockShard(flashId, i));
        }
        keys.add(MarketingRedisKeys.flashStockInit(flashId));
        redisTemplate.execute(initScript, keys,
                String.valueOf(remainingStock / SHARDS),
                String.valueOf(remainingStock % SHARDS),
                String.valueOf(ttlSeconds(ttl)));
    }

    private long ttlSeconds(Duration ttl) {
        long seconds = ttl != null ? ttl.getSeconds() : MarketingRedisKeys.DEFAULT_TTL.getSeconds();
        return Math.max(seconds, 1L);
    }
}
