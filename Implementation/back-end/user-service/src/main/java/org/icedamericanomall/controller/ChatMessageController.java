package org.icedamericanomall.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.domain.entity.ChatMessageEntity;
import org.icedamericanomall.service.ChatMessageService;
import org.noLazy.common.domain.Result;
import org.noLazy.common.utils.UserContext;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

/**
 * V3.3: 买家-商家消息 REST API + STOMP（DDD: Controller→Service→Mapper）。
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class ChatMessageController {

    private final ChatMessageService chatMessageService;
    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/chat.send")
    public void sendMessage(@Payload Map<String, Object> payload) {
        ChatMessageEntity msg = new ChatMessageEntity();
        msg.setMessageId(UUID.randomUUID().toString());
        msg.setConversationId(payload.get("conversationId").toString());
        msg.setSenderId(Long.valueOf(payload.get("senderId").toString()));
        msg.setSenderRole(payload.get("senderRole").toString());
        msg.setReceiverId(Long.valueOf(payload.get("receiverId").toString()));
        msg.setContent(payload.get("content").toString());
        msg.setContentType(payload.getOrDefault("contentType", "TEXT").toString());
        msg.setIsRead(0);
        msg.setCreateTime(LocalDateTime.now());
        chatMessageService.sendMessage(msg);
        messagingTemplate.convertAndSendToUser(msg.getReceiverId().toString(), "/queue/messages", msg);
    }

    @GetMapping("/api/chat/conversations")
    public Result<List<Map<String, Object>>> listConversations() {
        Long userId = UserContext.getUserId();
        return userId != null ? Result.ok(chatMessageService.listConversations(userId))
                : Result.ok(Collections.emptyList());
    }

    @GetMapping("/api/chat/conversations/{id}/messages")
    public Result<List<ChatMessageEntity>> getMessages(@PathVariable String id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int size) {
        return Result.ok(chatMessageService.getMessages(id, page, size));
    }

    @GetMapping("/api/chat/unread-count")
    public Result<Integer> getUnreadCount() {
        Long userId = UserContext.getUserId();
        return Result.ok(userId != null ? chatMessageService.getUnreadCount(userId) : 0);
    }
}
