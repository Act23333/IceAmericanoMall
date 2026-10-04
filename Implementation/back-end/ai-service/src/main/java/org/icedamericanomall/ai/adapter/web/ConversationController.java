package org.icedamericanomall.ai.adapter.web;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.ai.app.chat.ChatMessageManager;
import org.icedamericanomall.ai.domain.model.conversation.ConversationDetail;
import org.icedamericanomall.ai.domain.model.conversation.ConversationSummary;
import org.icedamericanomall.common.api.Result;
import org.icedamericanomall.common.security.UserContext;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.UnauthorizedException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;

/**
 * AI 会话管理（适配层，前台面）—— {@code /api/ai/conversations/**}。
 *
 * <p>与 {@code /api/chat/conversations}（IM 面）是同一份 {@code chat_message} 数据的两个 REST 面：
 * 二者共用 {@link ChatMessageManager} 的同一个聚合查询，只是出参形状不同（此处为
 * {@code ConversationSummary}/{@code ConversationDetail}）。Controller 仅做身份解析 + Result 包装。
 */
@Slf4j
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class ConversationController {

    private final ChatMessageManager chatMessageManager;

    /**
     * 会话列表（当前用户参与的会话）。
     *
     * @return 会话摘要列表；未登录返回空列表
     */
    @GetMapping("/conversations")
    public Result<List<ConversationSummary>> listConversations() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            return Result.ok(Collections.emptyList());
        }
        return Result.ok(chatMessageManager.listAiConversations(userId));
    }

    /**
     * 会话详情（含历史消息）。
     *
     * @param id 会话ID
     * @return 会话详情
     * @throws UnauthorizedException 未登录
     * @throws org.icedamericanomall.common.exception.ForbiddenException 非会话参与者
     */
    @GetMapping("/conversations/{id}")
    public Result<ConversationDetail> getConversation(@PathVariable String id) {
        return Result.ok(chatMessageManager.getConversationDetail(requireUserId(), id));
    }

    /**
     * 删除会话（清理该会话的 Agent 记忆）。
     *
     * @param id 会话ID
     * @throws UnauthorizedException 未登录
     * @throws org.icedamericanomall.common.exception.ForbiddenException 非会话参与者
     */
    @DeleteMapping("/conversations/{id}")
    public Result<Void> deleteConversation(@PathVariable String id) {
        chatMessageManager.deleteConversation(requireUserId(), id);
        return Result.ok();
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
