package org.icedamericanomall.controller;

import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.domain.dto.ConversationDetail;
import org.icedamericanomall.domain.dto.ConversationSummary;
import org.icedamericanomall.service.ConversationService;
import org.noLazy.common.domain.Result;
import org.noLazy.common.utils.UserContext;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;

/**
 * AI 会话管理 Interface 层 — V5.0 DDD 合规
 *
 * Redis 操作全部下沉到 {@link ConversationService}，Controller 仅做参数解析 + Result 包装。
 */
@Slf4j
@RestController
@RequestMapping("/api/ai")
public class ConversationController {

    private final ConversationService conversationService;

    public ConversationController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @GetMapping("/conversations")
    public Result<List<ConversationSummary>> listConversations() {
        var userInfo = UserContext.getUser();
        Long userId = userInfo != null ? userInfo.userId() : null;
        if (userId == null) return Result.ok(Collections.emptyList());
        return Result.ok(conversationService.listConversations(userId));
    }

    @GetMapping("/conversations/{id}")
    public Result<ConversationDetail> getConversation(@PathVariable String id) {
        var userInfo = UserContext.getUser();
        Long userId = userInfo != null ? userInfo.userId() : null;
        if (userId == null) return Result.error(401, "请先登录");
        return Result.ok(conversationService.getConversation(userId, id));
    }

    @DeleteMapping("/conversations/{id}")
    public Result<Void> deleteConversation(@PathVariable String id) {
        var userInfo = UserContext.getUser();
        Long userId = userInfo != null ? userInfo.userId() : null;
        if (userId == null) return Result.error(401, "请先登录");
        conversationService.deleteConversation(userId, id);
        return Result.ok();
    }
}
