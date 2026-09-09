package org.icedamericanomall.agent;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;

/**
 * LangChain4j AiServices interface — AI 智能客服。
 * <p>
 * 运行时通过 {@link dev.langchain4j.service.AiServices} 生成实现。
 * 工具方法通过 @Tool 注解声明在工具类中。
 * <p>
 * {@code @MemoryId} 参数用于 Per-User + Per-Conversation 会话隔离，
 * 由 Controller 层构造 {@code cs:{userId}:{conversationId}} 传入。
 */
public interface CustomerServiceAssistant {

    /**
     * 客服对话：FAQ问答、订单查询、转人工。
     * <pre>
     * Examples:
     *   "如何退货？"
     *   "我的订单 ORD-2026-001 到哪里了？"
     *   "我要投诉" (低置信度 → 转人工)
     * </pre>
     *
     * @param memoryId    会话隔离ID，格式: cs:{userId}:{conversationId}
     * @param userMessage 用户消息
     */
    String chat(@MemoryId String memoryId, @UserMessage String userMessage);
}
