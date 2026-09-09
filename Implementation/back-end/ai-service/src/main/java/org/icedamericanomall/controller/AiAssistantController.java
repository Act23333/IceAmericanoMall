package org.icedamericanomall.controller;

import dev.langchain4j.service.TokenStream;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.agent.StreamingShoppingAssistant;
import org.icedamericanomall.config.AiProperties;
import org.icedamericanomall.domain.dto.AiChatRequest;
import org.icedamericanomall.domain.dto.AiChatResponse;
import org.icedamericanomall.security.ContentSafetyFilter;
import org.noLazy.common.annotation.RateLimit;
import org.noLazy.common.domain.Result;
import org.noLazy.common.utils.UserContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.UUID;

/**
 * AI 商品助手 — LangChain4j AiServices (ReAct Agent)。
 * <p>
 * V2.5: Per-User + Per-Conversation 会话隔离，conversationId 由服务端生成。
 */
@Slf4j
@RestController
@RequestMapping("/api/ai")
public class AiAssistantController {

    @Autowired
    private AiProperties aiProperties;

    @Autowired(required = false)
    private StreamingShoppingAssistant streamingShoppingAssistant;

    @Autowired
    private org.icedamericanomall.manager.AiManager aiManager;

    @Autowired
    private ContentSafetyFilter safetyFilter;

    @RateLimit(key = "ip", limit = 20, duration = 60)
    @PostMapping("/chat")
    public Result<AiChatResponse> chat(@Valid @RequestBody AiChatRequest req) {
        String safetyCheck = safetyFilter.checkInput(req.message());
        if (safetyCheck != null) {
            return Result.error(3102, safetyCheck);
        }
        String conversationId = req.conversationId() != null
                ? req.conversationId()
                : UUID.randomUUID().toString();
        var userInfo = UserContext.getUser();
        String userKey = userInfo != null ? String.valueOf(userInfo.userId()) : "anonymous";
        String memoryId = "shopping:" + userKey + ":" + conversationId;

        String reply;
        if (aiProperties.isEnabled() && aiManager != null) {
            // V4.0 DDD: Application层编排（路由+缓存+多模型）全在 AiManager
            reply = aiManager.chat(memoryId, req.message());
        } else {
            reply = "AI 助手未启用。请设置 ai.enabled=true 并配置 DEEPSEEK_API_KEY。";
        }
        return Result.ok(new AiChatResponse(reply, conversationId));
    }

    /**
     * 流式聊天 — SSE 打字机效果。
     */
    @RateLimit(key = "ip", limit = 20, duration = 60)
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chatStream(@Valid @RequestBody AiChatRequest req) {
        String conversationId = req.conversationId() != null
                ? req.conversationId()
                : UUID.randomUUID().toString();
        var userInfo = UserContext.getUser();
        String userKey = userInfo != null ? String.valueOf(userInfo.userId()) : "anonymous";
        String memoryId = "shopping:" + userKey + ":" + conversationId;

        if (!aiProperties.isEnabled() || streamingShoppingAssistant == null) {
            return Flux.just(ServerSentEvent.<String>builder()
                    .data("AI 助手未启用。请设置 ai.enabled=true 并配置 DEEPSEEK_API_KEY。")
                    .build());
        }

        Sinks.Many<ServerSentEvent<String>> sink = Sinks.many().unicast().onBackpressureBuffer();
        TokenStream tokenStream = streamingShoppingAssistant.chat(memoryId, req.message());

        tokenStream.onPartialResponse(token -> sink.tryEmitNext(
                ServerSentEvent.<String>builder().data(token).build()))
                .onCompleteResponse(c -> {
                    sink.tryEmitNext(ServerSentEvent.<String>builder()
                            .data("[DONE]").build());
                    sink.tryEmitComplete();
                })
                .onError(error -> {
                    log.warn("StreamingShoppingAssistant error: {}", error.getMessage());
                    sink.tryEmitNext(ServerSentEvent.<String>builder()
                            .data("抱歉，生成回复时遇到了问题，请稍后重试。").build());
                    sink.tryEmitComplete();
                })
                .start();

        return sink.asFlux();
    }
}
