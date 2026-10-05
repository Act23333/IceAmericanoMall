package org.icedamericanomall.search.domain.gateway;

import java.util.List;

/**
 * 搜索关键词追踪（领域端口）—— 热搜榜与个人历史的统一契约。
 *
 * <p>实现方：{@code infrastructure.adapter.RedisKeywordTracker}（ZSet 热搜 + List 历史）。
 * 实现方需保证：Redis 异常时 {@link #recordKeyword(String)} 只记日志、不抛出（不影响检索主流程）；
 * {@link #getHotKeywordsByPrefix(String, int)} 异常时返回空列表、不抛出（不影响 SE5 联想主流程）。
 */
public interface KeywordTracker {

    /** 记录搜索关键词（累加热搜分）。 */
    void recordKeyword(String keyword);

    /** 热搜词 Top-N（按累计检索次数降序）。 */
    List<String> getHotKeywords(int limit);

    /**
     * 热搜词前缀联想（契约 SE5 源 1）—— 命中前缀的热搜词，按累计检索次数降序。
     *
     * <p>实现方需保证：Redis 异常返回空列表、不抛出（联想是输入辅助，故障不得影响主流程，
     * 与 {@link #recordKeyword(String)} 同一容错纪律）。
     *
     * @param prefix 前缀（调用方保证非空白）
     * @param limit  返回条数上限
     * @return 前缀命中的热搜词（去重后由 app 层统一合并）；无命中/异常为空列表
     */
    List<String> getHotKeywordsByPrefix(String prefix, int limit);

    /** 用户最近搜索词（按写入时间倒序取前 limit 条）。 */
    List<String> getSearchHistory(Long userId, int limit);
}
