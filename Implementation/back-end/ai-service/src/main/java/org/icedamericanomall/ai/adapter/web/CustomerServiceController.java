package org.icedamericanomall.ai.adapter.web;

import dev.langchain4j.service.TokenStream;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.ai.app.CsManager;
import org.icedamericanomall.ai.app.dto.AiChatRequest;
import org.icedamericanomall.ai.app.dto.AiChatResponse;
import org.icedamericanomall.common.annotation.RateLimit;
import org.icedamericanomall.common.api.Result;
import org.icedamericanomall.common.security.UserContext;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

/**
 * AI 智能客服 — LangChain4j AiServices (FAQ + 订单查询 + 转人工)。
 * <p>
 * 适配层只做参数校验 + 委托 {@link CsManager} + 包装 {@code Result}；
 * 内容安全校验、置信度转人工判定等编排逻辑全在应用层。
 *
 * <p><b>归位说明</b>：适配层子包按受众分组，本类为前台入口（{@code adapter/web}）。
 */
@Slf4j
@RestController
@RequestMapping("/api/ai/cs")
@RequiredArgsConstructor
public class CustomerServiceController {

    /** 流式结束标记。 */
    private static final String STREAM_DONE = "[DONE]";

    /** 流式异常兜底文案。 */
    private static final String STREAM_ERROR_MESSAGE = "抱歉，生成回复时遇到了问题，请稍后重试。";

    private final CsManager csManager;

    /**
     * 客服对话（非流式）。
     *
     * @param req 对话请求（message 必填，conversationId 可选）
     * @return 回复 + 会话ID；低置信度回答已自动转人工
     * @throws org.icedamericanomall.common.exception.BizException 输入未通过内容安全审核
     */
    @RateLimit(key = "ip", limit = 20, duration = 60)
    @PostMapping("/chat")
    public Result<AiChatResponse> chat(@Valid @RequestBody AiChatRequest req) {
        return Result.ok(csManager.chat(req, UserContext.getUserId()));
    }

    /**
     * 客服对话（SSE 打字机效果）。
     *
     * @param req 对话请求（message 必填，conversationId 可选）
     * @return SSE 数据流；AI 未启用时输出单条兜底文案
     */
    @RateLimit(key = "ip", limit = 20, duration = 60)
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chatStream(@Valid @RequestBody AiChatRequest req) {
        TokenStream tokenStream = csManager.stream(req, UserContext.getUserId());
        if (tokenStream == null) {
            return Flux.just(sse(csManager.unavailableMessage()));
        }
        Sinks.Many<ServerSentEvent<String>> sink = Sinks.many().unicast().onBackpressureBuffer();
        tokenStream.onPartialResponse(token -> sink.tryEmitNext(sse(token)))
                .onCompleteResponse(complete -> {
                    sink.tryEmitNext(sse(STREAM_DONE));
                    sink.tryEmitComplete();
                })
                .onError(error -> {
                    log.warn("StreamingCustomerServiceAssistant error: {}", error.getMessage());
                    sink.tryEmitNext(sse(STREAM_ERROR_MESSAGE));
                    sink.tryEmitComplete();
                })
                .start();
        return sink.asFlux();
    }

    private static ServerSentEvent<String> sse(String data) {
        return ServerSentEvent.<String>builder().data(data).build();
    }
}
