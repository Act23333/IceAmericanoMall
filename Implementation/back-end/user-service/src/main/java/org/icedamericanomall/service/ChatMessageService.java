package org.icedamericanomall.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.icedamericanomall.domain.entity.ChatMessageEntity;
import org.icedamericanomall.mapper.ChatMessageMapper;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;

/**
 * V3.3: 聊天消息 Service（DDD: Controller→Service→Mapper）。
 */
@Service
@RequiredArgsConstructor
public class ChatMessageService {

    private final ChatMessageMapper chatMessageMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    private static final String UNREAD_KEY = "chat:unread:";

    public void sendMessage(ChatMessageEntity msg) {
        chatMessageMapper.insert(msg);
        String cacheKey = "chat:recent:" + msg.getConversationId();
        redisTemplate.opsForList().rightPush(cacheKey, msg);
        redisTemplate.opsForList().trim(cacheKey, -100, -1);
        String unreadKey = UNREAD_KEY + msg.getReceiverId() + ":" + msg.getConversationId();
        redisTemplate.opsForValue().increment(unreadKey, 1);
        redisTemplate.expire(unreadKey, Duration.ofDays(30));
    }

    public List<Map<String, Object>> listConversations(Long userId) {
        List<ChatMessageEntity> list = chatMessageMapper.selectList(
                new LambdaQueryWrapper<ChatMessageEntity>()
                        .and(w -> w.eq(ChatMessageEntity::getSenderId, userId)
                                .or().eq(ChatMessageEntity::getReceiverId, userId))
                        .orderByDesc(ChatMessageEntity::getCreateTime));
        Set<String> seen = new HashSet<>();
        List<Map<String, Object>> result = new ArrayList<>();
        for (ChatMessageEntity m : list) {
            if (!seen.add(m.getConversationId())) continue;
            Integer unread = (Integer) redisTemplate.opsForValue()
                    .get(UNREAD_KEY + userId + ":" + m.getConversationId());
            result.add(Map.of("conversationId", m.getConversationId(),
                    "lastMessage", m.getContent(), "lastTime", m.getCreateTime(),
                    "unreadCount", unread != null ? unread : 0));
        }
        return result;
    }

    public List<ChatMessageEntity> getMessages(String conversationId, int page, int size) {
        return chatMessageMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<ChatMessageEntity>()
                        .eq(ChatMessageEntity::getConversationId, conversationId)
                        .orderByAsc(ChatMessageEntity::getCreateTime)).getRecords();
    }

    public int getUnreadCount(Long userId) {
        Set<String> keys = redisTemplate.keys(UNREAD_KEY + userId + ":*");
        int total = 0;
        if (keys != null) for (String key : keys) {
            Integer v = (Integer) redisTemplate.opsForValue().get(key);
            if (v != null) total += v;
        }
        return total;
    }
}
