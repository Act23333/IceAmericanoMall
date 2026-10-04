package org.icedamericanomall.ai.adapter.web;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.ai.adapter.assembler.ChatMessageConvertor;
import org.icedamericanomall.ai.adapter.request.ChatSendReq;
import org.icedamericanomall.ai.adapter.response.ChatConversationVO;
import org.icedamericanomall.ai.adapter.response.ChatMessageVO;
import org.icedamericanomall.ai.app.chat.ChatMessageManager;
import org.icedamericanomall.common.api.Result;
import org.icedamericanomall.common.security.UserContext;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.UnauthorizedException;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.Collections;
import java.util.List;

/**
 * 买家-商家消息 REST API + STOMP（适配层，前台面）—— 客服域（人工会话），
 * 与 AI 客服（{@link AiAssistantController}）及转人工（{@code CsManager} 转人工分支）同属本服务。
 *
 * <p>安全约定：STOMP 发送者身份取自会话 {@link Principal}（网关注入头解析），
 * 报文中的身份字段一律忽略；会话消息读取前先校验参与者归属。
 *
 * <p>STOMP 广播负载与历史消息出参均为 {@link ChatMessageVO}（字段与迁移前一致）；
 * 应用层返回 {@code ChatMessageDTO}，由 {@link ChatMessageConvertor} 装配为 VO。
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class ChatMessageController {

    private final ChatMessageManager chatMessageManager;
    private final SimpMessagingTemplate messagingTemplate;
    private final ChatMessageConvertor chatMessageConvertor;

    /**
     * STOMP: 发送消息 → 落库 + 缓存 + 推送给接收方。
     *
     * @param req       客户端报文（会话ID/接收者/内容，不含身份字段）
     * @param principal STOMP 会话身份（由 {@code StompAuthInterceptor} 装配）
     * @throws UnauthorizedException 会话未认证
     */
    @MessageMapping("/chat-send")
    public void sendMessage(@Valid @Payload ChatSendReq req, Principal principal) {
        ChatMessageVO message = chatMessageConvertor.toVO(
                chatMessageManager.sendMessage(chatMessageConvertor.toCommand(req, principal)));
        messagingTemplate.convertAndSendToUser(message.getReceiverId().toString(), "/queue/messages", message);
    }

    /**
     * 会话列表（含最后一条消息与未读数）。
     *
     * @return 当前用户的会话摘要；未登录返回空列表
     */
    @GetMapping("/api/chat/conversations")
    public Result<List<ChatConversationVO>> listConversations() {
        Long userId = UserContext.getUserId();
        return userId != null
                ? Result.ok(chatMessageConvertor.toConversationVOList(chatMessageManager.listConversations(userId)))
                : Result.ok(Collections.emptyList());
    }

    /**
     * 会话历史消息（仅参与者可读）。
     *
     * @param id   会话ID
     * @param page 页码（从 1 开始）
     * @param size 每页条数
     * @return 消息列表
     * @throws UnauthorizedException 未登录
     * @throws org.icedamericanomall.common.exception.ForbiddenException 非会话参与者
     */
    @GetMapping("/api/chat/conversations/{id}/messages")
    public Result<List<ChatMessageVO>> getMessages(@PathVariable String id,
                                                   @RequestParam(defaultValue = "1") int page,
                                                   @RequestParam(defaultValue = "50") int size) {
        Long userId = requireUserId();
        chatMessageManager.assertParticipant(id, userId);
        return Result.ok(chatMessageConvertor.toVOList(chatMessageManager.getMessages(id, page, size)));
    }

    /**
     * 未读总数。
     *
     * @return 未读消息总数；未登录返回 0
     */
    @GetMapping("/api/chat/unread-count")
    public Result<Integer> getUnreadCount() {
        Long userId = UserContext.getUserId();
        return Result.ok(userId != null ? chatMessageManager.getUnreadCount(userId) : 0);
    }

    /** 取当前登录用户ID，缺失即拒绝。 */
    private static Long requireUserId() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new UnauthorizedException(ErrorCode.UNAUTHORIZED);
        }
        return userId;
    }
}
