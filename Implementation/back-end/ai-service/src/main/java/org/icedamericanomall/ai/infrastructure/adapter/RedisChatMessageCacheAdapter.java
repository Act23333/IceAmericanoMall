package org.icedamericanomall.ai.infrastructure.adapter;

import lombok.RequiredArgsConstructor;
import org.icedamericanomall.ai.domain.port.ChatMessageCache;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 聊天消息缓存适配器（Redis 实现）。
 *
 * <p>Key 布局：{@code chat:recent:{conversationId}}（最近 100 条）+
 * {@code chat:unread:{receiverId}:{conversationId}}（未读计数，TTL 30 天）。
 * <p>按模式遍历一律使用 SCAN 游标，禁止 KEYS。
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

    /** SCAN 单批建议条数 */
    private static final long SCAN_COUNT = 500L;

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
        // 用 SCAN 增量游标替代 KEYS（KEYS 在大 key 空间下阻塞单线程 Redis）
        ScanOptions options = ScanOptions.scanOptions()
                .match(UNREAD_KEY + userId + ":*")
                .count(SCAN_COUNT)
                .build();
        int total = 0;
        try (Cursor<String> cursor = redisTemplate.scan(options)) {
            while (cursor.hasNext()) {
                Object value = redisTemplate.opsForValue().get(cursor.next());
                if (value instanceof Number number) {
                    total += number.intValue();
                }
            }
        }
        return total;
    }
}
