package org.icedAmericanoMall.agent;

/**
 * LangChain4j AiServices interface — AI 智能客服。
 * <p>
 * 运行时通过 {@link dev.langchain4j.service.AiServices} 生成实现。
 * 工具方法通过 @Tool 注解声明在工具类中。
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
     */
    String chat(String userMessage);
}
