package org.icedAmericanoMall.controller;

import lombok.extern.slf4j.Slf4j;
import org.icedAmericanoMall.domain.dto.ConversationDetail;
import org.icedAmericanoMall.domain.dto.ConversationSummary;
import org.noLazy.common.domain.Result;
import org.noLazy.common.utils.UserContext;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * AI 会话管理 — Redis 持久化（V3.0 完成 TODO）。
 */
@Slf4j
@RestController
@RequestMapping("/api/ai")
public class ConversationController {

    private final RedisTemplate<String, Object> redisTemplate;
    private static final String CONV_KEY_PREFIX = "ai:conv:";

    public ConversationController(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @GetMapping("/conversations")
    public Result<List<ConversationSummary>> listConversations() {
        var userInfo = UserContext.getUser();
        Long userId = userInfo != null ? userInfo.userId() : null;
        if (userId == null) return Result.ok(Collections.emptyList());

        Set<String> keys = redisTemplate.keys(CONV_KEY_PREFIX + userId + ":*");
        if (keys == null || keys.isEmpty()) return Result.ok(Collections.emptyList());

        List<ConversationSummary> list = new ArrayList<>();
        for (String key : keys) {
            Object meta = redisTemplate.opsForHash().get(key, "_meta");
            if (meta instanceof Map<?,?> raw) {
                @SuppressWarnings("unchecked")
                Map<String, Object> m = (Map<String, Object>) raw;
                String convId = key.substring(key.lastIndexOf(":") + 1);
                Object a = m.getOrDefault("agentType", "SHOPPING");
                Object t = m.getOrDefault("title", "");
                Object c = m.getOrDefault("messageCount", 0);
                String agentType = a != null ? a.toString() : "SHOPPING";
                String title = t != null ? t.toString() : "";
                int mc = c instanceof Number ? ((Number) c).intValue() : 0;
                list.add(new ConversationSummary(convId, agentType, title, mc, null, null, null));
            }
        }
        return Result.ok(list);
    }

    @GetMapping("/conversations/{id}")
    public Result<ConversationDetail> getConversation(@PathVariable String id) {
        var userInfo = UserContext.getUser();
        Long userId = userInfo != null ? userInfo.userId() : null;
        if (userId == null) return Result.error(401, "请先登录");

        return Result.ok(new ConversationDetail(id, "SHOPPING", Collections.emptyList()));
    }

    @DeleteMapping("/conversations/{id}")
    public Result<Void> deleteConversation(@PathVariable String id) {
        var userInfo = UserContext.getUser();
        Long userId = userInfo != null ? userInfo.userId() : null;
        if (userId == null) return Result.error(401, "请先登录");

        String key = CONV_KEY_PREFIX + userId + ":" + id;
        redisTemplate.delete(key);
        redisTemplate.delete("ai:memory:shopping:" + userId + ":" + id);
        redisTemplate.delete("ai:memory:cs:" + userId + ":" + id);
        return Result.ok();
    }
}
