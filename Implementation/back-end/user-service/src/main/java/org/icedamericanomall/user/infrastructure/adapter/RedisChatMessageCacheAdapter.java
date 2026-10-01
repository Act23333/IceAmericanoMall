package org.icedamericanomall.user.infrastructure.adapter;

import lombok.RequiredArgsConstructor;
import org.icedamericanomall.user.app.port.ChatMessageCache;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Set;

/**
 * 聊天消息缓存适配器（Redis 实现）。
 *
 * <p>Key 布局：{@code chat:recent:{conversationId}}（最近 100 条）+
 * {@code chat:unread:{receiverId}:{conversationId}}（未读计数，TTL 30 天）。
 */
@Component
@RequiredArgsConstructor
public class RedisChatMessageCacheAdapter implements ChatMessageCache {

    private static final String RECENT_KEY = "chat:recent:";
    private static final String UNREAD_KEY = "chat:unread:";

    /** 最近消息保留条数 */
    private static final long RECENT_KEEP = 100L;

    /** 未读计数有效期 */
    private static final Duration UNREAD_TTL = Duration.ofDays(30);

    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public void cacheRecent(String conversationId, Object message) {
        String cacheKey = RECENT_KEY + conversationId;
        redisTemplate.opsForList().rightPush(cacheKey, message);
        redisTemplate.opsForList().trim(cacheKey, -RECENT_KEEP, -1);
    }

    @Override
    public void incrementUnread(Long receiverId, String conversationId) {
        String unreadKey = UNREAD_KEY + receiverId + ":" + conversationId;
        redisTemplate.opsForValue().increment(unreadKey, 1);
        redisTemplate.expire(unreadKey, UNREAD_TTL);
    }

    @Override
    public int getUnread(Long userId, String conversationId) {
        Object value = redisTemplate.opsForValue().get(UNREAD_KEY + userId + ":" + conversationId);
        return value instanceof Number number ? number.intValue() : 0;
    }

    @Override
    public int totalUnread(Long userId) {
        Set<String> keys = redisTemplate.keys(UNREAD_KEY + userId + ":*");
        int total = 0;
        if (keys != null) {
            for (String key : keys) {
                Object value = redisTemplate.opsForValue().get(key);
                if (value instanceof Number number) {
                    total += number.intValue();
                }
            }
        }
        return total;
    }
}
