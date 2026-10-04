package org.icedamericanomall.search.domain.gateway;

import java.util.List;

/**
 * 搜索关键词追踪（领域端口）—— 热搜榜与个人历史的统一契约。
 *
 * <p>实现方：{@code infrastructure.adapter.RedisKeywordTracker}（ZSet 热搜 + List 历史）。
 * 实现方需保证：Redis 异常时 {@link #recordKeyword(String)} 只记日志、不抛出（不影响检索主流程）。
 */
public interface KeywordTracker {

    /** 记录搜索关键词（累加热搜分）。 */
    void recordKeyword(String keyword);

    /** 热搜词 Top-N（按累计检索次数降序）。 */
    List<String> getHotKeywords(int limit);

    /** 用户最近搜索词（按写入时间倒序取前 limit 条）。 */
    List<String> getSearchHistory(Long userId, int limit);
}
