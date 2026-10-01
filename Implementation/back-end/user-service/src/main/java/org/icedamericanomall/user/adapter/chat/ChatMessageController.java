package org.icedamericanomall.user.adapter.chat;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.user.adapter.response.ChatMessageVO;
import org.icedamericanomall.user.app.chat.ChatMessageManager;
import org.icedamericanomall.common.domain.Result;
import org.icedamericanomall.common.domain.UserContext;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 买家-商家消息 REST API + STOMP（适配层）。
 *
 * <p>STOMP 广播负载为 {@link ChatMessageVO}（字段与重构前的实体报文一致）。
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class ChatMessageController {

    private final ChatMessageManager chatMessageManager;
    private final SimpMessagingTemplate messagingTemplate;

    /** STOMP: 发送消息 → 落库 + 缓存 + 推送给接收方。 */
    @MessageMapping("/chat.send")
    public void sendMessage(@Payload Map<String, Object> payload) {
        ChatMessageVO message = chatMessageManager.sendMessage(
                payload.get("conversationId").toString(),
                Long.valueOf(payload.get("senderId").toString()),
                payload.get("senderRole").toString(),
                Long.valueOf(payload.get("receiverId").toString()),
                payload.get("content").toString(),
                payload.getOrDefault("contentType", "TEXT").toString());
        messagingTemplate.convertAndSendToUser(message.getReceiverId().toString(), "/queue/messages", message);
    }

    /** 会话列表（含最后一条消息与未读数）。 */
    @GetMapping("/api/chat/conversations")
    public Result<List<Map<String, Object>>> listConversations() {
        Long userId = UserContext.getUserId();
        return userId != null ? Result.ok(chatMessageManager.listConversations(userId))
                : Result.ok(Collections.emptyList());
    }

    /** 会话历史消息。 */
    @GetMapping("/api/chat/conversations/{id}/messages")
    public Result<List<ChatMessageVO>> getMessages(@PathVariable String id,
                                                   @RequestParam(defaultValue = "1") int page,
                                                   @RequestParam(defaultValue = "50") int size) {
        return Result.ok(chatMessageManager.getMessages(id, page, size));
    }

    /** 未读总数。 */
    @GetMapping("/api/chat/unread-count")
    public Result<Integer> getUnreadCount() {
        Long userId = UserContext.getUserId();
        return Result.ok(userId != null ? chatMessageManager.getUnreadCount(userId) : 0);
    }
}
