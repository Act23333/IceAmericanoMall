package org.icedamericanomall.ai.domain.port;

/**
 * 聊天消息缓存（应用层端口，基础设施层用 Redis 实现）。
 *
 * <p>Key 布局：{@code chat:recent:{conversationId}}（最近消息列表，保留末尾 100 条）+
 * {@code chat:unread:{receiverId}:{conversationId}}（未读计数，TTL 30 天）。
 */
public interface ChatMessageCache {

    /** 追加一条会话消息到最近列表（仅保留末尾 100 条）。 */
    void cacheRecent(String conversationId, Object message);

    /** 未读计数 +1。 */
    void incrementUnread(Long receiverId, String conversationId);

    /** 读某会话未读数（无则 0）。 */
    int getUnread(Long userId, String conversationId);

    /** 读用户全部会话未读总数。 */
    int totalUnread(Long userId);
}
