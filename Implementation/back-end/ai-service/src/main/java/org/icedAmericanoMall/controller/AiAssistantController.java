package org.icedAmericanoMall.controller;

import dev.langchain4j.service.TokenStream;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.icedAmericanoMall.agent.ShoppingAssistant;
import org.icedAmericanoMall.agent.StreamingShoppingAssistant;
import org.icedAmericanoMall.config.AiProperties;
import org.icedAmericanoMall.domain.dto.AiChatRequest;
import org.icedAmericanoMall.domain.dto.AiChatResponse;
import org.icedAmericanoMall.routing.ModelRouter;
import org.icedAmericanoMall.security.ContentSafetyFilter;
import org.noLazy.common.annotation.RateLimit;
import org.noLazy.common.domain.Result;
import org.noLazy.common.utils.UserContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
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
    private ShoppingAssistant shoppingAssistant;

    @Autowired(required = false)
    private StreamingShoppingAssistant streamingShoppingAssistant;

    @Autowired(required = false)
    @Qualifier("qwenShoppingAssistant")
    private ShoppingAssistant qwenShoppingAssistant;

    @Autowired
    private ModelRouter modelRouter;

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
        if (aiProperties.isEnabled() && shoppingAssistant != null) {
            // V3.0: 语义缓存 — FAQ 命中直接返回
            reply = modelRouter.getCachedResponse(req.message());
            if (reply != null) {
                log.debug("Semantic cache hit");
                return Result.ok(new AiChatResponse(reply, conversationId));
            }

            // V3.0: 多模型路由 — 复杂query走Qwen，简单query走DeepSeek
            boolean isComplex = qwenShoppingAssistant != null
                    && modelRouter.estimateComplexity(req.message()) >=
                       aiProperties.getRouting().getComplexityThreshold();
            ShoppingAssistant selectedAgent = isComplex ? qwenShoppingAssistant : shoppingAssistant;

            String modelName = isComplex ? "Qwen" : "DeepSeek";
            log.debug("Chat: memoryId={}, model={}, complexity={}",
                    memoryId, modelName, modelRouter.estimateComplexity(req.message()));
            reply = selectedAgent.chat(memoryId, req.message());

            // V3.0: 语义缓存 — 缓存FAQ回答
            modelRouter.cacheResponse(req.message(), reply);
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
