package org.icedAmericanoMall.config;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

/**
 * Redis 持久化的 ChatMemory 存储 —— Per-User + Per-Conversation 隔离。
 * <p>
 * Key pattern: {@code ai:memory:{memoryId}}
 * 其中 memoryId 由调用方构造，格式为 {@code {agentType}:{userId}:{conversationId}}。
 * <p>
 * TTL 24 小时，到期自动清除。每条会话最多保留所配置的消息轮数（由 MessageWindowChatMemory 控制窗口大小）。
 */
@Slf4j
@RequiredArgsConstructor
public class RedisChatMemoryStore implements ChatMemoryStore {

    private final RedisTemplate<String, Object> redisTemplate;
    private final Duration ttl;

    private static final String KEY_PREFIX = "ai:memory:";

    @Override
    @SuppressWarnings("unchecked")
    public List<ChatMessage> getMessages(Object memoryId) {
        String key = buildKey(memoryId);
        Object cached = redisTemplate.opsForValue().get(key);
        if (cached instanceof List) {
            redisTemplate.expire(key, ttl);
            return (List<ChatMessage>) cached;
        }
        return Collections.emptyList();
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        String key = buildKey(memoryId);
        redisTemplate.opsForValue().set(key, messages, ttl);
    }

    @Override
    public void deleteMessages(Object memoryId) {
        String key = buildKey(memoryId);
        redisTemplate.delete(key);
        log.debug("Deleted chat memory: {}", key);
    }

    private String buildKey(Object memoryId) {
        return KEY_PREFIX + memoryId;
    }
}
