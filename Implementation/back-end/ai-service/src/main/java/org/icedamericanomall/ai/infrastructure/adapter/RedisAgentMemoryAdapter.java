package org.icedamericanomall.ai.infrastructure.adapter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.ai.domain.port.AgentMemoryPort;
import org.icedamericanomall.ai.infrastructure.config.RedisChatMemoryStore;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis 版 Agent 会话记忆清理适配器（基础设施层）—— 实现 {@link AgentMemoryPort}。
 *
 * <p>键布局与 memoryId 组装统一取自 {@link RedisChatMemoryStore}（键构造唯一入口，
 * 禁止硬编码前缀）；会话删除时清理购物助手与智能客服两条记忆，与原删除语义逐字一致。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisAgentMemoryAdapter implements AgentMemoryPort {

    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public void evictConversationMemory(Long userId, String conversationId) {
        String userKey = String.valueOf(userId);
        redisTemplate.delete(RedisChatMemoryStore.memoryKey(RedisChatMemoryStore.memoryId(
                RedisChatMemoryStore.SHOPPING_AGENT, userKey, conversationId)));
        redisTemplate.delete(RedisChatMemoryStore.memoryKey(RedisChatMemoryStore.memoryId(
                RedisChatMemoryStore.CS_AGENT, userKey, conversationId)));
    }
}
