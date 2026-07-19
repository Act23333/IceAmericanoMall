package org.icedAmericanoMall.config;

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import org.icedAmericanoMall.agent.CustomerServiceAssistant;
import org.icedAmericanoMall.agent.ShoppingAssistant;
import org.icedAmericanoMall.tool.OrderLookupTool;
import org.icedAmericanoMall.tool.SearchTool;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AI Agent 配置 — 仅在 ai.enabled=true 时创建 Bean。
 * <p>
 * Spring AI 管理 ChatClient（通过 application.yml 的 spring.ai.openai.* 自动配置）。
 * LangChain4j 管理 AiServices Agent（接口 + @Tool 注解 → 运行时代理）。
 */
@Configuration
public class AiAgentConfig {

    /**
     * 购物助手 Agent — ReAct 模式：LLM 推理 → 工具调用 → 结果汇总。
     */
    @Bean
    @ConditionalOnProperty(name = "ai.enabled", havingValue = "true")
    public ShoppingAssistant shoppingAssistant(OpenAiChatModel langchain4jChatModel, SearchTool searchTool) {
        ChatMemory memory = MessageWindowChatMemory.withMaxMessages(10);
        return AiServices.builder(ShoppingAssistant.class)
                .chatModel(langchain4jChatModel)
                .chatMemory(memory)
                .tools(searchTool)
                .build();
    }

    /**
     * 客服 Agent — FAQ + 订单查询 + 转人工。
     */
    @Bean
    @ConditionalOnProperty(name = "ai.enabled", havingValue = "true")
    public CustomerServiceAssistant customerServiceAssistant(
            OpenAiChatModel langchain4jChatModel, OrderLookupTool orderLookupTool) {
        ChatMemory memory = MessageWindowChatMemory.withMaxMessages(20);
        return AiServices.builder(CustomerServiceAssistant.class)
                .chatModel(langchain4jChatModel)
                .chatMemory(memory)
                .tools(orderLookupTool)
                .build();
    }
}
