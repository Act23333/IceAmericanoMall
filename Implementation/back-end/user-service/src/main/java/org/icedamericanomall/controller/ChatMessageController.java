package org.icedamericanomall.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.domain.entity.ChatMessageEntity;
import org.icedamericanomall.mapper.ChatMessageMapper;
import org.noLazy.common.domain.Result;
import org.noLazy.common.utils.UserContext;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

/**
 * V3.3: 买家-商家消息 REST API + STOMP 消息处理。
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class ChatMessageController {

    private final ChatMessageMapper chatMessageMapper;
    private final SimpMessagingTemplate messagingTemplate;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String UNREAD_KEY = "chat:unread:";

    // ── STOMP WebSocket 消息接收 ──

    @MessageMapping("/chat.send")
    public void sendMessage(@Payload Map<String, Object> payload) {
        Long senderId = Long.valueOf(payload.get("senderId").toString());
        String senderRole = payload.get("senderRole").toString();
        Long receiverId = Long.valueOf(payload.get("receiverId").toString());
        String content = payload.get("content").toString();
        String conversationId = payload.get("conversationId").toString();
        String contentType = payload.getOrDefault("contentType", "TEXT").toString();

        ChatMessageEntity msg = new ChatMessageEntity();
        msg.setMessageId(UUID.randomUUID().toString());
        msg.setConversationId(conversationId);
        msg.setSenderId(senderId);
        msg.setSenderRole(senderRole);
        msg.setReceiverId(receiverId);
        msg.setContent(content);
        msg.setContentType(contentType);
        msg.setIsRead(0);
        msg.setCreateTime(LocalDateTime.now());
        chatMessageMapper.insert(msg);

        // 实时推送到接收者
        messagingTemplate.convertAndSendToUser(
                receiverId.toString(), "/queue/messages", msg);

        // 未读计数 +1
        String unreadKey = UNREAD_KEY + receiverId + ":" + conversationId;
        redisTemplate.opsForValue().increment(unreadKey, 1);
        redisTemplate.expire(unreadKey, Duration.ofDays(30));

        // 缓存最近消息
        String cacheKey = "chat:recent:" + conversationId;
        redisTemplate.opsForList().rightPush(cacheKey, msg);
        redisTemplate.opsForList().trim(cacheKey, -100, -1); // 保留最近100条
    }

    // ── REST API ──

    @GetMapping("/api/chat/conversations")
    public Result<List<Map<String, Object>>> listConversations() {
        Long userId = UserContext.getUserId();
        if (userId == null) return Result.ok(Collections.emptyList());

        // 查询该用户参与的所有会话（sender或receiver）
        var wrapper = new LambdaQueryWrapper<ChatMessageEntity>()
                .and(w -> w.eq(ChatMessageEntity::getSenderId, userId)
                        .or().eq(ChatMessageEntity::getReceiverId, userId))
                .orderByDesc(ChatMessageEntity::getCreateTime)
                .groupBy(ChatMessageEntity::getConversationId);
        List<ChatMessageEntity> list = chatMessageMapper.selectList(wrapper);

        Set<String> seen = new HashSet<>();
        List<Map<String, Object>> result = new ArrayList<>();
        for (ChatMessageEntity m : list) {
            if (!seen.add(m.getConversationId())) continue;
            Integer unread = (Integer) redisTemplate.opsForValue()
                    .get(UNREAD_KEY + userId + ":" + m.getConversationId());
            result.add(Map.of(
                    "conversationId", m.getConversationId(),
                    "lastMessage", m.getContent(),
                    "lastTime", m.getCreateTime(),
                    "unreadCount", unread != null ? unread : 0));
        }
        return Result.ok(result);
    }

    @GetMapping("/api/chat/conversations/{id}/messages")
    public Result<List<ChatMessageEntity>> getMessages(@PathVariable String id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int size) {
        Page<ChatMessageEntity> pageResult = chatMessageMapper.selectPage(
                new Page<>(page, size),
                new LambdaQueryWrapper<ChatMessageEntity>()
                        .eq(ChatMessageEntity::getConversationId, id)
                        .orderByAsc(ChatMessageEntity::getCreateTime));
        return Result.ok(pageResult.getRecords());
    }

    @GetMapping("/api/chat/unread-count")
    public Result<Integer> getUnreadCount() {
        Long userId = UserContext.getUserId();
        if (userId == null) return Result.ok(0);
        Set<String> keys = redisTemplate.keys(UNREAD_KEY + userId + ":*");
        int total = 0;
        if (keys != null) {
            for (String key : keys) {
                Integer v = (Integer) redisTemplate.opsForValue().get(key);
                if (v != null) total += v;
            }
        }
        return Result.ok(total);
    }
}
