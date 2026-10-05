package org.icedamericanomall.search.infrastructure.adapter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.search.domain.gateway.KeywordTracker;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 搜索关键词 Redis 支撑组件（热搜榜 + 个人搜索历史）。
 *
 * <p>抽自 ES / DB 两套 {@code SearchService} 实现的重复代码：
 * 热搜 ZSet（{@code search:hot:keywords}）与历史 List（{@code search:history:{userId}}）
 * 是同一份数据契约，必须只有一处定义，避免两种实现口径漂移。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisKeywordTracker implements KeywordTracker {

    private static final String HOT_KEYWORDS_KEY = "search:hot:keywords";
    private static final String HISTORY_KEY_PREFIX = "search:history:";

    /**
     * 前缀联想（SE5）的热搜扫描窗口：取热度 Top-N 后内存过滤前缀。
     * 选型说明：热搜 ZSet 的 score 是检索次数，{@code ZREVRANGEBYLEX} 按成员字典序返回、
     * 忽略 score，会破坏热搜排序语义；窗口扫描保持热度降序且实现简单，
     * 窗口外的长尾热搜词不参与联想（可接受的降级，联想只要 Top 热词）。
     */
    private static final int PREFIX_SCAN_WINDOW = 100;

    private final RedisTemplate<String, String> redisTemplate;

    /** 热搜词 Top-N（按累计检索次数降序）。 */
    public List<String> getHotKeywords(int limit) {
        var top = redisTemplate.opsForZSet().reverseRange(HOT_KEYWORDS_KEY, 0, limit - 1);
        return top != null ? new ArrayList<>(top) : Collections.emptyList();
    }

    /**
     * 热搜词前缀联想（SE5 源 1）—— 热度 Top 窗口内按前缀过滤，保持热度降序。
     *
     * <p>前缀匹配不区分大小写（与商品名源 MySQL ci collation 口径对齐，中文不受影响）；
     * Redis 异常返回空列表、不抛出（端口契约：联想故障不得影响主流程）。
     */
    @Override
    public List<String> getHotKeywordsByPrefix(String prefix, int limit) {
        try {
            Set<String> top = redisTemplate.opsForZSet()
                    .reverseRange(HOT_KEYWORDS_KEY, 0, PREFIX_SCAN_WINDOW - 1L);
            if (top == null || top.isEmpty()) {
                return Collections.emptyList();
            }
            String prefixLower = prefix.toLowerCase(Locale.ROOT);
            return top.stream()
                    .filter(keyword -> keyword != null
                            && keyword.toLowerCase(Locale.ROOT).startsWith(prefixLower))
                    .limit(limit)
                    .toList();
        } catch (Exception e) {
            log.warn("热搜前缀联想失败（按无命中处理）: prefix={}, cause={}", prefix, e.getMessage());
            return Collections.emptyList();
        }
    }

    /** 用户最近搜索词（按写入时间倒序取前 limit 条）。 */
    public List<String> getSearchHistory(Long userId, int limit) {
        String key = HISTORY_KEY_PREFIX + userId;
        List<String> history = redisTemplate.opsForList().range(key, 0, limit - 1);
        return history != null ? history : Collections.emptyList();
    }

    /**
     * 记录搜索关键词（累加热搜分）。Redis 不可用时不得影响检索主流程，仅记 warn。
     */
    public void recordKeyword(String keyword) {
        try {
            redisTemplate.opsForZSet().incrementScore(HOT_KEYWORDS_KEY, keyword, 1);
        } catch (Exception e) {
            log.warn("记录热搜关键词失败: keyword={}, cause={}", keyword, e.getMessage());
        }
    }
}
