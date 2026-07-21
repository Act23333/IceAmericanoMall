package org.icedAmericanoMall.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedAmericanoMall.domain.dto.ConversationDetail;
import org.icedAmericanoMall.domain.dto.ConversationSummary;
import org.noLazy.common.domain.Result;
import org.noLazy.common.utils.UserContext;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;

/**
 * AI 会话管理 — 对话列表、详情、删除。
 * <p>
 * V2.5 新增，提供会话级别的 CRUD 操作。当前为骨架实现（数据库表已建，Redis ChatMemory 已就绪），
 * 完整持久化逻辑留待后续迭代补充。
 */
@Slf4j
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class ConversationController {

    /**
     * 获取当前用户的所有会话列表。
     */
    @GetMapping("/conversations")
    public Result<List<ConversationSummary>> listConversations() {
        var userInfo = UserContext.getUser();
        Long userId = userInfo != null ? userInfo.userId() : null;
        if (userId == null) {
            return Result.ok(Collections.emptyList());
        }
        // TODO: 从数据库 ai_conversation 表查询用户会话列表
        log.debug("listConversations for userId={}", userId);
        return Result.ok(Collections.emptyList());
    }

    /**
     * 获取指定会话详情（含消息历史）。
     */
    @GetMapping("/conversations/{id}")
    public Result<ConversationDetail> getConversation(@PathVariable String id) {
        var userInfo = UserContext.getUser();
        Long userId = userInfo != null ? userInfo.userId() : null;
        if (userId == null) {
            return Result.error(401, "请先登录");
        }
        // TODO: 从 Redis + 数据库查询会话详情
        log.debug("getConversation id={}, userId={}", id, userId);
        return Result.ok(new ConversationDetail(id, "SHOPPING", Collections.emptyList()));
    }

    /**
     * 删除/归档指定会话。
     */
    @DeleteMapping("/conversations/{id}")
    public Result<Void> deleteConversation(@PathVariable String id) {
        var userInfo = UserContext.getUser();
        Long userId = userInfo != null ? userInfo.userId() : null;
        if (userId == null) {
            return Result.error(401, "请先登录");
        }
        // TODO: 标记数据库 ai_conversation 为已删除，清除 Redis ChatMemory
        log.debug("deleteConversation id={}, userId={}", id, userId);
        return Result.ok();
    }
}
