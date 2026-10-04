package org.icedamericanomall.search.infrastructure.adapter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.search.domain.gateway.KeywordTracker;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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

    private final RedisTemplate<String, String> redisTemplate;

    /** 热搜词 Top-N（按累计检索次数降序）。 */
    public List<String> getHotKeywords(int limit) {
        var top = redisTemplate.opsForZSet().reverseRange(HOT_KEYWORDS_KEY, 0, limit - 1);
        return top != null ? new ArrayList<>(top) : Collections.emptyList();
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
