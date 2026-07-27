package org.icedamericanomall.manager;

import lombok.RequiredArgsConstructor;
import org.icedamericanomall.agent.ShoppingAssistant;
import org.icedamericanomall.routing.ModelRouter;
import org.springframework.stereotype.Component;

/**
 * V4.0 DDD: AI编排 Manager（Application层）。
 * 职责: Agent路由编排 + 多模型选择 + 语义缓存决策。
 */
@Component
@RequiredArgsConstructor
public class AiManager {

    private final ShoppingAssistant shoppingAssistant;
    private final ModelRouter modelRouter;

    /** 购物助手: 路由决策(简单→DeepSeek, 复杂→Qwen) */
    public String chat(String memoryId, String message) {
        // 检查语义缓存
        String cached = modelRouter.getCachedResponse(message);
        if (cached != null) return cached;

        String reply = shoppingAssistant.chat(memoryId, message);
        modelRouter.cacheResponse(message, reply);
        return reply;
    }
}
