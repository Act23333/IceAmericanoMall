package org.icedamericanomall.config;

import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.service.AiServices;
import org.icedamericanomall.agent.CustomerServiceAssistant;
import org.icedamericanomall.agent.ShoppingAssistant;
import org.icedamericanomall.agent.StreamingCustomerServiceAssistant;
import org.icedamericanomall.agent.StreamingShoppingAssistant;
import org.icedamericanomall.routing.ModelRouter;
import org.icedamericanomall.tool.OrderLookupTool;
import org.icedamericanomall.tool.ProductDetailTool;
import org.icedamericanomall.tool.RAGSearchTool;
import org.icedamericanomall.tool.SearchTool;
import org.icedamericanomall.tool.UserProfileTool;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Duration;

/**
 * AI Agent 配置 — 仅在 ai.enabled=true 时创建 Bean。
 * <p>
 * V2.5 会话隔离：使用 {@code chatMemoryProvider} + Redis {@code ChatMemoryStore}
 * 实现 Per-User + Per-Conversation 的 ChatMemory 隔离。
 * memoryId 格式：{@code {agentType}:{userId}:{conversationId}}，
 * 由 Controller 层从 JWT 提取 userId 并构造。
 */
@Configuration
public class AiAgentConfig {

    private static final int SHOPPING_MAX_MESSAGES = 10;
    private static final int CS_MAX_MESSAGES = 20;
    private static final Duration MEMORY_TTL = Duration.ofHours(24);

    /**
     * Redis ChatMemory 持久化存储。
     */
    @Bean
    @ConditionalOnProperty(name = "ai.enabled", havingValue = "true")
    public RedisChatMemoryStore redisChatMemoryStore(RedisTemplate<String, Object> redisTemplate) {
        return new RedisChatMemoryStore(redisTemplate, MEMORY_TTL);
    }

    /**
     * 购物助手 Agent — ReAct 模式：LLM 推理 → 工具调用 → 结果汇总。
     * <p>
     * 每次请求通过 {@code chatMemoryProvider} 创建独立的 ChatMemory，
     * memoryId 由 Controller 传入（如 "shopping:1001:conv-abc123"）。
     */
    @Bean
    @ConditionalOnProperty(name = "ai.enabled", havingValue = "true")
    public ShoppingAssistant shoppingAssistant(
            OpenAiChatModel langchain4jChatModel,
            SearchTool searchTool,
            ProductDetailTool productDetailTool,
            UserProfileTool userProfileTool,
            RedisChatMemoryStore memoryStore) {
        return AiServices.builder(ShoppingAssistant.class)
                .chatModel(langchain4jChatModel)
                .chatMemoryProvider(memoryId -> MessageWindowChatMemory.builder()
                        .id(memoryId)
                        .maxMessages(SHOPPING_MAX_MESSAGES)
                        .chatMemoryStore(memoryStore)
                        .build())
                .tools(searchTool, productDetailTool, userProfileTool)
                .build();
    }

    /**
     * 客服 Agent — FAQ + 订单查询 + 转人工。
     * <p>
     * {@code chatMemoryProvider} 保证不同用户/会话的上下文完全隔离。
     */
    @Bean
    @ConditionalOnProperty(name = "ai.enabled", havingValue = "true")
    public CustomerServiceAssistant customerServiceAssistant(
            OpenAiChatModel langchain4jChatModel,
            OrderLookupTool orderLookupTool,
            RAGSearchTool ragSearchTool,
            RedisChatMemoryStore memoryStore) {
        return AiServices.builder(CustomerServiceAssistant.class)
                .chatModel(langchain4jChatModel)
                .chatMemoryProvider(memoryId -> MessageWindowChatMemory.builder()
                        .id(memoryId)
                        .maxMessages(CS_MAX_MESSAGES)
                        .chatMemoryStore(memoryStore)
                        .build())
                .tools(orderLookupTool, ragSearchTool)
                .build();
    }

    // ──────────────── V2.5.1 流式 Agent ────────────────

    /**
     * 流式购物助手 Agent — SSE 打字机效果。
     */
    @Bean
    @ConditionalOnProperty(name = "ai.enabled", havingValue = "true")
    public StreamingShoppingAssistant streamingShoppingAssistant(
            OpenAiStreamingChatModel streamingChatModel,
            SearchTool searchTool,
            ProductDetailTool productDetailTool,
            UserProfileTool userProfileTool,
            RedisChatMemoryStore memoryStore) {
        return AiServices.builder(StreamingShoppingAssistant.class)
                .streamingChatModel(streamingChatModel)
                .chatMemoryProvider(memoryId -> MessageWindowChatMemory.builder()
                        .id(memoryId)
                        .maxMessages(SHOPPING_MAX_MESSAGES)
                        .chatMemoryStore(memoryStore)
                        .build())
                .tools(searchTool, productDetailTool, userProfileTool)
                .build();
    }

    /**
     * 流式客服 Agent — SSE 打字机效果。
     */
    @Bean
    @ConditionalOnProperty(name = "ai.enabled", havingValue = "true")
    public StreamingCustomerServiceAssistant streamingCustomerServiceAssistant(
            OpenAiStreamingChatModel streamingChatModel,
            OrderLookupTool orderLookupTool,
            RAGSearchTool ragSearchTool,
            RedisChatMemoryStore memoryStore) {
        return AiServices.builder(StreamingCustomerServiceAssistant.class)
                .streamingChatModel(streamingChatModel)
                .chatMemoryProvider(memoryId -> MessageWindowChatMemory.builder()
                        .id(memoryId)
                        .maxMessages(CS_MAX_MESSAGES)
                        .chatMemoryStore(memoryStore)
                        .build())
                .tools(orderLookupTool, ragSearchTool)
                .build();
    }

    // ──────────────── V3.0 多模型路由 — Qwen Agent ────────────────

    @Bean
    @ConditionalOnProperty(name = "ai.enabled", havingValue = "true")
    public OpenAiChatModel qwenChatModel(AiProperties props) {
        var qwen = props.getModels().getQwen();
        return OpenAiChatModel.builder()
                .apiKey(qwen.getApiKey())
                .baseUrl(qwen.getBaseUrl())
                .modelName(qwen.getModel())
                .temperature(0.7)
                .timeout(Duration.ofSeconds(60))
                .build();
    }

    @Bean("qwenShoppingAssistant")
    @ConditionalOnProperty(name = "ai.enabled", havingValue = "true")
    public ShoppingAssistant qwenShoppingAssistant(
            @Qualifier("qwenChatModel") OpenAiChatModel qwenModel,
            SearchTool searchTool, ProductDetailTool productDetailTool,
            UserProfileTool userProfileTool, RedisChatMemoryStore memoryStore) {
        return AiServices.builder(ShoppingAssistant.class)
                .chatModel(qwenModel)
                .chatMemoryProvider(memoryId -> MessageWindowChatMemory.builder()
                        .id(memoryId).maxMessages(SHOPPING_MAX_MESSAGES)
                        .chatMemoryStore(memoryStore).build())
                .tools(searchTool, productDetailTool, userProfileTool)
                .build();
    }
}
