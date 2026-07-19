package org.icedAmericanoMall.controller;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.icedAmericanoMall.agent.CustomerServiceAssistant;
import org.icedAmericanoMall.config.AiProperties;
import org.icedAmericanoMall.domain.dto.AiChatRequest;
import org.icedAmericanoMall.domain.dto.AiChatResponse;
import org.noLazy.common.domain.Result;
import org.noLazy.common.utils.UserContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * AI 智能客服 — LangChain4j AiServices (FAQ + 订单查询 + 转人工模板)。
 * <p>
 * V2.5: Per-User + Per-Conversation 会话隔离。
 */
@Slf4j
@RestController
@RequestMapping("/api/ai/cs")
public class CustomerServiceController {

    @Autowired
    private AiProperties aiProperties;

    @Autowired(required = false)
    private CustomerServiceAssistant customerServiceAssistant;

    @PostMapping("/chat")
    public Result<AiChatResponse> chat(@Valid @RequestBody AiChatRequest req) {
        String conversationId = req.getConversationId() != null
                ? req.getConversationId()
                : UUID.randomUUID().toString();
        Long userId = UserContext.getUser();
        String userKey = userId != null ? userId.toString() : "anonymous";
        String memoryId = "cs:" + userKey + ":" + conversationId;

        String reply;
        if (aiProperties.isEnabled() && customerServiceAssistant != null) {
            log.debug("CustomerServiceAssistant chat: memoryId={}, message={}", memoryId, req.getMessage());
            reply = customerServiceAssistant.chat(memoryId, req.getMessage());
        } else {
            reply = "AI 客服未启用。请设置 ai.enabled=true 并配置 DEEPSEEK_API_KEY。";
        }
        return Result.ok(AiChatResponse.builder()
                .reply(reply)
                .conversationId(conversationId)
                .build());
    }
}
