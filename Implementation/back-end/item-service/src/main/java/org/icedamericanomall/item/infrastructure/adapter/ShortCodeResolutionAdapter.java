package org.icedamericanomall.item.infrastructure.adapter;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.item.domain.enums.ShortCodeHitSource;
import org.icedamericanomall.item.domain.model.spu.ShortCode;
import org.icedamericanomall.item.domain.model.spu.Spu;
import org.icedamericanomall.item.domain.port.ShortCodeResolver;
import org.icedamericanomall.item.domain.repository.SpuRepository;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.atomic.LongAdder;

/**
 * 短码解析适配器 —— {@link ShortCodeResolver} 端口的三级缓存实现（ADR-063 §决策 8，批次 B7b）。
 *
 * <pre>
 * L1  Caffeine 本地     最大 10k 条；映射不可变 ⇒ 正向 expireAfterAccess 10min、负向 45s
 * L2  Redis             item:shortcode:{code} → SPU 业务键（正向 7 天 + LRU，负向 45s）
 * L3  DB（真相源）       spu.short_code 等值查（uk_short_code）
 * </pre>
 *
 * <p><b>为什么必须落 DB、Redis 不能是唯一副本</b>：短码会<b>印在纸上</b>（包装/海报/名片）
 * 并被分享出去，物理介质无法撤回；Redis 是缓存（会被 flush、会被淘汰、会不可用），
 * 唯一真相必须是 {@code spu.short_code}。验收硬断言就是「清空 L1+L2 后仍能解析成功」。
 *
 * <p><b>三道防线（逐条对应实现）</b>：
 * <ol>
 *   <li><b>防穿透</b>：不存在的短码写负缓存（本地 45s / Redis 45s，落在契约的 30–60s 区间），
 *       批量猜码只打一次库；</li>
 *   <li><b>防击穿</b>：本地重建走 Caffeine {@code get(key, loader)} —— 同一 key 的并发加载
 *       由 Caffeine 串行化（single-flight），不会整批打库；</li>
 *   <li><b>Redis 不可用 → 回落 DB（fail-open 到下一层）</b>：Redis 的读/写异常一律只记 WARN，
 *       <b>绝不</b>把「Redis 挂了」当成「短码不存在」。反向纪律同样明确：<b>DB 故障不得伪装成 404</b>
 *       —— DB 异常向上抛 {@code INTERNAL_ERROR(500)}，不写负缓存（否则一次抖动会把好码缓存成「不存在」）。</li>
 * </ol>
 *
 * <p><b>不做失效监听</b>：映射与商品同生命周期、永不变更、永不回收（软删保留占位），
 * 因此没有任何「写后失效」事件需要订阅 —— 少一条监听就是少一处可漂移的路径。
 * 负缓存是唯一需要过期的东西（新码随时可能被创建）。
 *
 * <p><b>领域层零技术依赖</b>：Caffeine / Redis 只出现在本类（infrastructure），
 * 应用层只看见 {@link ShortCodeResolver}。
 */
@Slf4j
@Component
public class ShortCodeResolutionAdapter implements ShortCodeResolver {

    /** L2 Redis 键前缀（跨实例共享；不与其它服务共享 —— ADR-034 跨服务不共享 Redis 键）。 */
    static final String REDIS_KEY_PREFIX = "item:shortcode:";

    /**
     * 负缓存哨兵值。
     *
     * <p>不能复用空串 / {@code null}：Redis 的 {@code GET} 对「键不存在」返回 {@code null}，
     * 而「键存在但值为空」返回 {@code ""} —— 用同一个值表达两种含义会让「未缓存」与
     * 「已知不存在」互相冒充。{@code "!"} 不可能是合法短码映射值（业务键恒为 32-hex）。
     */
    static final String NEGATIVE_MARK = "!";

    /** L1 条目上限（商品量级；超出按 LRU 淘汰，仅损失一次本地命中）。 */
    private static final long LOCAL_MAX_SIZE = 10_000L;

    /** L1 正向 TTL：写后 10 分钟未被访问即淘汰（映射不可变，故用 access 语义）。 */
    private static final Duration LOCAL_EXPIRE_AFTER_ACCESS = Duration.ofMinutes(10);

    /** 负缓存 TTL（契约区间 30–60s）：过期后重新问真相源，避免「新码被判不存在」长期留存。 */
    private static final Duration NEGATIVE_TTL = Duration.ofSeconds(45);

    /** L2 正向 TTL：映射不变，给一个长 TTL（+ 键空间可 LRU 淘汰）即可，不做永不过期。 */
    private static final Duration REDIS_POSITIVE_TTL = Duration.ofDays(7);

    private final SpuRepository spuRepository;
    private final StringRedisTemplate redisTemplate;

    /** L1 缓存（值：SPU 业务键 或 {@link #NEGATIVE_MARK}）。 */
    private final Cache<String, String> localCache;

    /** 四类命中计数（按 ordinal 索引，避免 EnumMap 的并发写语义争议）。 */
    private final LongAdder[] counters = new LongAdder[ShortCodeHitSource.values().length];

    public ShortCodeResolutionAdapter(SpuRepository spuRepository, StringRedisTemplate redisTemplate) {
        this.spuRepository = spuRepository;
        this.redisTemplate = redisTemplate;
        for (int i = 0; i < counters.length; i++) {
            counters[i] = new LongAdder();
        }
        this.localCache = Caffeine.newBuilder()
                .maximumSize(LOCAL_MAX_SIZE)
                .expireAfter(new Expiry<String, String>() {
                    @Override
                    public long expireAfterCreate(String key, String value, long currentTime) {
                        return ttlNanos(value);
                    }

                    @Override
                    public long expireAfterUpdate(String key, String value, long currentTime,
                                                  long currentDuration) {
                        return ttlNanos(value);
                    }

                    @Override
                    public long expireAfterRead(String key, String value, long currentTime,
                                                long currentDuration) {
                        // 正向 = expireAfterAccess（读一下就续期）；负向固定 45s，不被读续命
                        return ttlNanos(value);
                    }
                })
                .build();
    }

    @Override
    public String resolve(String shortCode) {
        // 内部防御：形态非法即「未命中」（适配层已用 ShortCode#require 做过 400 fail-fast）
        if (!ShortCode.isValid(shortCode)) {
            return null;
        }
        // L1 快路径：命中即返回，不触碰 Redis / DB
        String cached = localCache.getIfPresent(shortCode);
        if (cached != null) {
            if (NEGATIVE_MARK.equals(cached)) {
                count(ShortCodeHitSource.NEGATIVE);
                return null;
            }
            count(ShortCodeHitSource.LOCAL);
            return cached;
        }
        // L1 未命中 → 重建（Caffeine 保证同一 key 的并发重建只执行一次 = single-flight）
        String loaded = localCache.get(shortCode, this::loadFromRemote);
        if (loaded == null || NEGATIVE_MARK.equals(loaded)) {
            return null;
        }
        return loaded;
    }

    @Override
    public long hitCount(ShortCodeHitSource source) {
        return source == null ? 0L : counters[source.ordinal()].sum();
    }

    // ==================== 缓存重建（L2 → L3） ====================

    /**
     * 重建路径：先问 L2 Redis，未命中再问 L3 DB 并回填两级缓存。
     *
     * <p>本方法只会被一个线程对同一个短码执行（Caffeine 的 per-key 串行化），
     * 因此命中计数不会因并发重建而重复计入同一来源。
     */
    private String loadFromRemote(String shortCode) {
        String remote = readRedis(shortCode);
        if (remote != null) {
            if (NEGATIVE_MARK.equals(remote)) {
                count(ShortCodeHitSource.NEGATIVE);
                return NEGATIVE_MARK;
            }
            count(ShortCodeHitSource.REDIS);
            return remote;
        }
        Spu spu = loadFromDb(shortCode);
        if (spu == null || spu.getSpuId() == null) {
            writeRedis(shortCode, NEGATIVE_MARK, NEGATIVE_TTL);
            count(ShortCodeHitSource.NEGATIVE);
            return NEGATIVE_MARK;
        }
        writeRedis(shortCode, spu.getSpuId(), REDIS_POSITIVE_TTL);
        count(ShortCodeHitSource.DB);
        return spu.getSpuId();
    }

    /**
     * L3：真相源等值查。
     *
     * <p><b>DB 故障必须 loudly 失败</b>：把 DB 异常归一成「查不到」会让一次数据库抖动
     * 被前端渲染成「商品不存在」，且负缓存会把这个错误结论留存 45s。故此处包装成
     * {@code INTERNAL_ERROR(500)} 上抛，<b>不写负缓存</b>。
     */
    private Spu loadFromDb(String shortCode) {
        try {
            return spuRepository.findByShortCode(shortCode);
        } catch (RuntimeException e) {
            log.error("[short-code][db-fail] 短码解析访问真相源失败（不下发负缓存，按系统故障处理）: code={}",
                    shortCode, e);
            throw new BizException(ErrorCode.INTERNAL_ERROR, "短码解析失败，请稍后重试");
        }
    }

    // ==================== L2 Redis（全程 fail-open） ====================

    /**
     * 读 L2；任何异常 → {@code null}（= 视为未命中，继续问 L3）。
     *
     * <p><b>这正是「Redis 挂了 ≠ 404」的落点</b>：异常被吞掉只留 WARN，解析改由 DB 完成 ——
     * 可用性优先，且不影响正确性（DB 是真相源）。
     */
    private String readRedis(String shortCode) {
        if (redisTemplate == null) {
            return null;
        }
        try {
            return redisTemplate.opsForValue().get(REDIS_KEY_PREFIX + shortCode);
        } catch (Exception e) {
            log.warn("[short-code][redis-read-fail-open] Redis 不可用，回落真相源: code={}, cause={}",
                    shortCode, e.getMessage());
            return null;
        }
    }

    /** 写 L2；失败只记 WARN（缓存故障不影响解析结果，也不影响正确性）。 */
    private void writeRedis(String shortCode, String value, Duration ttl) {
        if (redisTemplate == null) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(REDIS_KEY_PREFIX + shortCode, value, ttl);
        } catch (Exception e) {
            log.warn("[short-code][redis-write-fail-open] 回填 Redis 失败（忽略）: code={}, cause={}",
                    shortCode, e.getMessage());
        }
    }

    /** 负向条目固定 45s，正向按 {@link #LOCAL_EXPIRE_AFTER_ACCESS}。 */
    private static long ttlNanos(String value) {
        Duration ttl = NEGATIVE_MARK.equals(value) ? NEGATIVE_TTL : LOCAL_EXPIRE_AFTER_ACCESS;
        return ttl.toNanos();
    }

    private void count(ShortCodeHitSource source) {
        counters[source.ordinal()].increment();
    }
}
