package org.icedamericanomall.manager;

import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.agent.ShoppingAssistant;
import org.icedamericanomall.config.AiProperties;
import org.icedamericanomall.routing.ModelRouter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * V4.0 DDD: AI编排 Manager（Application层）。
 * <p>
 * 职责: Agent路由编排 + 多模型选择 + 语义缓存决策。
 * Controller 只做参数校验+委托Manager，业务逻辑全在 Manager 层。
 */
@Slf4j
@Component
public class AiManager {

    private final ShoppingAssistant shoppingAssistant;
    private final ShoppingAssistant qwenAssistant;
    private final ModelRouter modelRouter;
    private final AiProperties aiProperties;

    public AiManager(ShoppingAssistant shoppingAssistant,
                     @Qualifier("qwenShoppingAssistant") ShoppingAssistant qwenAssistant,
                     ModelRouter modelRouter, AiProperties aiProperties) {
        this.shoppingAssistant = shoppingAssistant;
        this.qwenAssistant = qwenAssistant;
        this.modelRouter = modelRouter;
        this.aiProperties = aiProperties;
    }

    /** Application层: Agent路由+缓存编排 */
    public String chat(String memoryId, String message) {
        // 语义缓存检查
        String cached = modelRouter.getCachedResponse(message);
        if (cached != null) {
            log.debug("Semantic cache hit");
            return cached;
        }

        // 多模型路由决策
        boolean isComplex = qwenAssistant != null
                && modelRouter.estimateComplexity(message) >=
                   aiProperties.getRouting().getComplexityThreshold();
        ShoppingAssistant agent = isComplex ? qwenAssistant : shoppingAssistant;

        log.debug("Chat: model={}, complexity={}",
                isComplex ? "Qwen" : "DeepSeek", modelRouter.estimateComplexity(message));
        String reply = agent.chat(memoryId, message);

        // 缓存FAQ回答
        modelRouter.cacheResponse(message, reply);
        return reply;
    }
}
