package org.icedamericanomall.ai.infrastructure.config;

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
 * <p>
 * 键布局与 memoryId 组装是本类独有的知识，跨层（应用层编排 / 会话删除）一律经
 * {@link #memoryId(String, String, String)} 与 {@link #memoryKey(String)} 取用，禁止各处硬编码前缀。
 */
@Slf4j
@RequiredArgsConstructor
public class RedisChatMemoryStore implements ChatMemoryStore {

    /** 购物助手 Agent 类型标识 */
    public static final String SHOPPING_AGENT = "shopping";

    /** 智能客服 Agent 类型标识 */
    public static final String CS_AGENT = "cs";

    private static final String KEY_PREFIX = "ai:memory:";

    private final RedisTemplate<String, Object> redisTemplate;
    private final Duration ttl;

    /**
     * 组装 langchain4j memoryId：{@code {agentType}:{userKey}:{conversationId}}。
     *
     * @param agentType      Agent 类型（{@link #SHOPPING_AGENT} / {@link #CS_AGENT}）
     * @param userKey        用户标识（未登录时为 anonymous）
     * @param conversationId 会话ID
     * @return memoryId
     */
    public static String memoryId(String agentType, String userKey, String conversationId) {
        return agentType + ":" + userKey + ":" + conversationId;
    }

    /**
     * memoryId → Redis 键（键构造的唯一入口）。
     *
     * @param memoryId langchain4j memoryId
     * @return Redis 键
     */
    public static String memoryKey(String memoryId) {
        return KEY_PREFIX + memoryId;
    }


    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        String key = buildKey(memoryId);
        Object cached = redisTemplate.opsForValue().get(key);
        if (cached instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof ChatMessage) {
            redisTemplate.expire(key, ttl);
            @SuppressWarnings("unchecked")
            List<ChatMessage> messages = (List<ChatMessage>) cached;
            return messages;
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
        return memoryKey(String.valueOf(memoryId));
    }
}
