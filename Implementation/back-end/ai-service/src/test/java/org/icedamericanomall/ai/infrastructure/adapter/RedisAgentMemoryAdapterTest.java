package org.icedamericanomall.ai.infrastructure.adapter;

import org.icedamericanomall.ai.domain.port.AgentMemoryPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * RedisAgentMemoryAdapter 单元测试 —— 会话删除时清理购物助手与客服两条 Agent 记忆。
 *
 * <p>键布局为 {@code ai:memory:{agentType}:{userId}:{conversationId}}，
 * 与 {@code RedisChatMemoryStore} 的记忆键逐字一致（否则删除落空、记忆残留）。
 */
@ExtendWith(MockitoExtension.class)
class RedisAgentMemoryAdapterTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Test
    @DisplayName("清理会话记忆 — 删除购物助手与客服两条键，键格式与记忆存储一致")
    void shouldDeleteBothAgentMemoryKeys_whenEvictConversation() {
        AgentMemoryPort port = new RedisAgentMemoryAdapter(redisTemplate);

        port.evictConversationMemory(1L, "c-1");

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(redisTemplate, times(2)).delete(keyCaptor.capture());
        assertEquals(
                List.of("ai:memory:shopping:1:c-1", "ai:memory:cs:1:c-1"),
                keyCaptor.getAllValues(),
                "两条记忆键必须与 RedisChatMemoryStore 键布局逐字一致");
    }
}
