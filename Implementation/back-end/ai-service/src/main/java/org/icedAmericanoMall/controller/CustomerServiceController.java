package org.icedAmericanoMall.controller;

import dev.langchain4j.service.TokenStream;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.icedAmericanoMall.agent.CustomerServiceAssistant;
import org.icedAmericanoMall.agent.StreamingCustomerServiceAssistant;
import org.icedAmericanoMall.config.AiProperties;
import org.icedAmericanoMall.domain.dto.AiChatRequest;
import org.icedAmericanoMall.domain.dto.AiChatResponse;
import org.icedAmericanoMall.security.ContentSafetyFilter;
import org.noLazy.common.annotation.RateLimit;
import org.noLazy.common.domain.Result;
import org.noLazy.common.enums.ErrorCode;
import org.noLazy.common.utils.UserContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.UUID;

/**
 * AI 智能客服 — LangChain4j AiServices (FAQ + 订单查询 + 转人工模板)。
 * <p>
 * V2.5: Per-User + Per-Conversation 会话隔离。
 * V2.5.1: SSE 流式端点。
 */
@Slf4j
@RestController
@RequestMapping("/api/ai/cs")
public class CustomerServiceController {

    @Autowired
    private AiProperties aiProperties;

    @Autowired(required = false)
    private CustomerServiceAssistant customerServiceAssistant;

    @Autowired(required = false)
    private StreamingCustomerServiceAssistant streamingCustomerServiceAssistant;

    @Autowired
    private ContentSafetyFilter safetyFilter;

    @RateLimit(key = "ip", limit = 20, duration = 60)
    @PostMapping("/chat")
    public Result<AiChatResponse> chat(@Valid @RequestBody AiChatRequest req) {
        String safetyCheck = safetyFilter.checkInput(req.getMessage());
        if (safetyCheck != null) {
            return Result.error(ErrorCode.AI_CONTENT_FILTERED.getCode(),
                    ErrorCode.AI_CONTENT_FILTERED.getMessage());
        }
        String conversationId = req.getConversationId() != null
                ? req.getConversationId()
                : UUID.randomUUID().toString();
        var userInfo = UserContext.getUser();
        String userKey = userInfo != null ? String.valueOf(userInfo.userId()) : "anonymous";
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

    /**
     * 流式客服 — SSE 打字机效果。
     */
    @RateLimit(key = "ip", limit = 20, duration = 60)
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chatStream(@Valid @RequestBody AiChatRequest req) {
        String conversationId = req.getConversationId() != null
                ? req.getConversationId()
                : UUID.randomUUID().toString();
        var userInfo = UserContext.getUser();
        String userKey = userInfo != null ? String.valueOf(userInfo.userId()) : "anonymous";
        String memoryId = "cs:" + userKey + ":" + conversationId;

        if (!aiProperties.isEnabled() || streamingCustomerServiceAssistant == null) {
            return Flux.just(ServerSentEvent.<String>builder()
                    .data("AI 客服未启用。请设置 ai.enabled=true 并配置 DEEPSEEK_API_KEY。")
                    .build());
        }

        Sinks.Many<ServerSentEvent<String>> sink = Sinks.many().unicast().onBackpressureBuffer();
        TokenStream tokenStream = streamingCustomerServiceAssistant.chat(memoryId, req.getMessage());

        tokenStream.onPartialResponse(token -> sink.tryEmitNext(
                ServerSentEvent.<String>builder().data(token).build()))
                .onCompleteResponse(c -> {
                    sink.tryEmitNext(ServerSentEvent.<String>builder()
                            .data("[DONE]").build());
                    sink.tryEmitComplete();
                })
                .onError(error -> {
                    log.warn("StreamingCustomerServiceAssistant error: {}", error.getMessage());
                    sink.tryEmitNext(ServerSentEvent.<String>builder()
                            .data("抱歉，生成回复时遇到了问题，请稍后重试。").build());
                    sink.tryEmitComplete();
                })
                .start();

        return sink.asFlux();
    }
}
