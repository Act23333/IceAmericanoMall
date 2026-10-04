package org.icedamericanomall.ai.app;

import dev.langchain4j.service.TokenStream;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.ai.infrastructure.agent.ShoppingAssistant;
import org.icedamericanomall.ai.infrastructure.agent.StreamingShoppingAssistant;
import org.icedamericanomall.ai.infrastructure.config.AiProperties;
import org.icedamericanomall.ai.infrastructure.config.RedisChatMemoryStore;
import org.icedamericanomall.ai.app.dto.AiChatRequest;
import org.icedamericanomall.ai.app.dto.AiChatResponse;
import org.icedamericanomall.ai.infrastructure.routing.ModelRouter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * V4.0 DDD: AI 编排 Manager（应用层）。
 * <p>
 * 职责：会话ID/memoryId 构造 + 多模型选择 + 语义缓存 + 启用判断与兜底文案。
 * Controller 只做参数校验并委托本类，业务分支不再散落在适配层。
 * <p>
 * 依赖的 Agent/路由 Bean 仅在 {@code ai.enabled=true} 时存在，故以 {@code @Nullable}
 * 注入：不用 AI 能力时本 Bean 仍可装配，服务可正常启动。
 */
@Slf4j
@Component
public class AiManager {

    /** 未登录用户的 memoryId 占位标识。 */
    private static final String ANONYMOUS_USER = "anonymous";

    /** AI 未启用时的兜底文案。 */
    private static final String UNAVAILABLE_MESSAGE =
            "AI 助手未启用。请设置 ai.enabled=true 并配置 DEEPSEEK_API_KEY。";

    private final AiProperties aiProperties;
    private final ShoppingAssistant shoppingAssistant;
    private final ShoppingAssistant qwenAssistant;
    private final ModelRouter modelRouter;
    private final StreamingShoppingAssistant streamingAssistant;

    public AiManager(AiProperties aiProperties,
                     @Nullable ShoppingAssistant shoppingAssistant,
                     @Nullable @Qualifier("qwenShoppingAssistant") ShoppingAssistant qwenAssistant,
                     @Nullable ModelRouter modelRouter,
                     @Nullable StreamingShoppingAssistant streamingShoppingAssistant) {
        this.aiProperties = aiProperties;
        this.shoppingAssistant = shoppingAssistant;
        this.qwenAssistant = qwenAssistant;
        this.modelRouter = modelRouter;
        this.streamingAssistant = streamingShoppingAssistant;
    }

    /**
     * 同步对话：语义缓存 → 复杂度路由 → Agent 调用 → 缓存回答。
     *
     * @param req    对话请求
     * @param userId 当前用户ID（未登录为 null）
     * @return 回复与规范化后的会话ID
     */
    public AiChatResponse chat(AiChatRequest req, Long userId) {
        String conversationId = resolveConversationId(req.conversationId());
        if (shoppingAssistant == null) {
            return new AiChatResponse(UNAVAILABLE_MESSAGE, conversationId);
        }
        String memoryId = memoryId(userId, conversationId);
        String reply = routeAndChat(memoryId, req.message());
        return new AiChatResponse(reply, conversationId);
    }

    /**
     * 流式对话：AI 未启用或流式 Agent 缺失时返回 null，由适配层输出兜底文案。
     *
     * @param req    对话请求
     * @param userId 当前用户ID（未登录为 null）
     * @return TokenStream；不可用时为 null
     */
    @Nullable
    public TokenStream stream(AiChatRequest req, Long userId) {
        if (streamingAssistant == null) {
            return null;
        }
        return streamingAssistant.chat(memoryId(userId, resolveConversationId(req.conversationId())),
                req.message());
    }

    /** 组装会话 memoryId：{@code shopping:{userId}:{conversationId}}。 */
    public String memoryId(Long userId, String conversationId) {
        String userKey = userId == null ? ANONYMOUS_USER : String.valueOf(userId);
        return RedisChatMemoryStore.memoryId(RedisChatMemoryStore.SHOPPING_AGENT, userKey, conversationId);
    }

    /** 会话ID 为空时生成 UUID。 */
    public String resolveConversationId(String requested) {
        return requested != null && !requested.isBlank() ? requested : UUID.randomUUID().toString();
    }

    /** AI 未启用时的兜底文案（适配层流式分支复用，避免文案重复）。 */
    public String unavailableMessage() {
        return UNAVAILABLE_MESSAGE;
    }

    /** 语义缓存命中则直接返回，否则按复杂度选择模型并调用。 */
    private String routeAndChat(String memoryId, String message) {
        String cached = modelRouter != null ? modelRouter.getCachedResponse(message) : null;
        if (cached != null) {
            log.debug("Semantic cache hit");
            return cached;
        }
        ShoppingAssistant agent = selectAgent(message);
        String reply = agent.chat(memoryId, message);
        if (modelRouter != null) {
            modelRouter.cacheResponse(message, reply);
        }
        return reply;
    }

    /** 复杂度 >= 阈值且备用 Agent 可用时路由到 Qwen。 */
    private ShoppingAssistant selectAgent(String message) {
        if (qwenAssistant == null || modelRouter == null) {
            return shoppingAssistant;
        }
        int complexity = modelRouter.estimateComplexity(message);
        boolean complex = complexity >= aiProperties.getRouting().getComplexityThreshold();
        log.debug("Chat routing: model={}, complexity={}", complex ? "Qwen" : "DeepSeek", complexity);
        return complex ? qwenAssistant : shoppingAssistant;
    }
}
