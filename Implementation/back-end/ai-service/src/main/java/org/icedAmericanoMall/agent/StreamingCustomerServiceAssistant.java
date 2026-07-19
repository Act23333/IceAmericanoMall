package org.icedAmericanoMall.agent;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;

/**
 * LangChain4j AiServices interface — AI 智能客服（流式）。
 * <p>
 * 与 {@link CustomerServiceAssistant} 功能相同，但使用 {@link TokenStream} 返回类型
 * 实现 SSE 流式响应（打字机效果）。
 */
public interface StreamingCustomerServiceAssistant {

    /**
     * 流式客服对话。
     *
     * @param memoryId    会话隔离ID，格式: cs:{userId}:{conversationId}
     * @param userMessage 用户消息
     * @return TokenStream，由 Controller 适配为 SSE Flux
     */
    TokenStream chat(@MemoryId String memoryId, @UserMessage String userMessage);
}
