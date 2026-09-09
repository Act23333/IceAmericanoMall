package org.icedamericanomall.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.domain.dto.ConversationDetail;
import org.icedamericanomall.domain.dto.ConversationSummary;
import org.icedamericanomall.service.ConversationService;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * AI 会话管理实现 — Redis 持久化
 *
 * Infrastructure 层负责 Redis 操作，Controller 不得直接操作。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationServiceImpl implements ConversationService {

    private final RedisTemplate<String, Object> redisTemplate;
    private static final String CONV_KEY_PREFIX = "ai:conv:";

    @Override
    public List<ConversationSummary> listConversations(Long userId) {
        if (userId == null) return Collections.emptyList();

        Set<String> keys = redisTemplate.keys(CONV_KEY_PREFIX + userId + ":*");
        if (keys == null || keys.isEmpty()) return Collections.emptyList();

        List<ConversationSummary> list = new ArrayList<>();
        for (String key : keys) {
            Object meta = redisTemplate.opsForHash().get(key, "_meta");
            if (meta instanceof Map<?, ?> raw) {
                @SuppressWarnings("unchecked")
                Map<String, Object> m = (Map<String, Object>) raw;
                String convId = key.substring(key.lastIndexOf(":") + 1);
                String agentType = String.valueOf(m.getOrDefault("agentType", "SHOPPING"));
                String title = String.valueOf(m.getOrDefault("title", ""));
                int mc = m.get("messageCount") instanceof Number n ? n.intValue() : 0;
                list.add(new ConversationSummary(convId, agentType, title, mc, null, null, null));
            }
        }
        return list;
    }

    @Override
    public ConversationDetail getConversation(Long userId, String conversationId) {
        return new ConversationDetail(conversationId, "SHOPPING", Collections.emptyList());
    }

    @Override
    public void deleteConversation(Long userId, String conversationId) {
        String key = CONV_KEY_PREFIX + userId + ":" + conversationId;
        redisTemplate.delete(key);
        redisTemplate.delete("ai:memory:shopping:" + userId + ":" + conversationId);
        redisTemplate.delete("ai:memory:cs:" + userId + ":" + conversationId);
        log.debug("AI 会话已删除: userId={}, conversationId={}", userId, conversationId);
    }
}
