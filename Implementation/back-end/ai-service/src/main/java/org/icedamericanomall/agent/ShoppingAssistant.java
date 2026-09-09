package org.icedamericanomall.agent;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;

/**
 * LangChain4j AiServices interface — 商品导购助手。
 * <p>
 * LangChain4j 在运行时生成代理实现，自动处理 LLM 调用→工具选择→结果汇总的 ReAct 循环。
 * 工具方法通过 @Tool 注解声明在 {@link org.icedamericanomall.tool.SearchTool} 中。
 * <p>
 * {@code @MemoryId} 参数用于 Per-User + Per-Conversation 会话隔离，
 * 由 Controller 层构造 {@code shopping:{userId}:{conversationId}} 传入。
 */
public interface ShoppingAssistant {

    /**
     * 自然语言商品搜索，支持多轮对话。
     * <pre>
     * Examples:
     *   "500以内适合夏天穿的透气跑鞋"
     *   "有什么性价比高的蓝牙耳机？"
     *   "跟之前那个比有什么不同？" (多轮上下文)
     * </pre>
     *
     * @param memoryId    会话隔离ID，格式: shopping:{userId}:{conversationId}
     * @param userMessage 用户消息
     */
    String chat(@MemoryId String memoryId, @UserMessage String userMessage);
}
