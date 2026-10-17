package org.icedamericanomall.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Client;
import feign.Feign;
import feign.Request;
import feign.RequestLine;
import feign.Response;
import org.icedamericanomall.common.api.ErrorInfo;
import org.icedamericanomall.common.aspect.GlobalExceptionHandler;
import org.icedamericanomall.common.constant.SecurityHeaders;
import org.icedamericanomall.common.enums.ErrorCategory;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.common.filter.TraceIdFilter;
import org.icedamericanomall.exception.UpstreamErrorDecoder;
import org.icedamericanomall.exception.UpstreamException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * <b>★ 批次 ⑤b：{@code X-Trace-Id} 的两跳真链路</b>（不是各测一半的假链路）。
 *
 * <p>链路（全部用<b>真实组件</b>，只有网络是进程内替身）：
 * <pre>
 * 第一跳（调用方服务）
 *   TraceIdFilter 已把网关 X-Trace-Id 写进 MDC
 *     → DefaultFeignConfig#requestInterceptor 注入 X-Trace-Id
 *     → Feign 真实调用流水线（builder → RequestInterceptor → Client）
 * 第二跳（下游服务 = 本测试里的 DownstreamService）
 *     → TraceIdFilter 读该头写 MDC（缺失则生成 UUID）
 *     → Controller 抛 BizException（/internal/** 真实路径）
 *     → GlobalExceptionHandler 输出 ErrorInfo（traceId 取自 MDC）
 *     → 500 + JSON 错误体
 * 回到第一跳
 *     → UpstreamErrorDecoder 解析错误体 → UpstreamException.traceId
 * </pre>
 *
 * <p><b>为什么必须两跳一起测</b>：只测「拦截器注入了头」证明不了下游会把它写进 MDC；
 * 只测「错误体带 traceId」证明不了这个值来自调用方（下游自己生成的 UUID 也是非空）。
 * 两者拼起来才有意义 —— 断言的是<b>同一个值</b>贯穿两跳。
 *
 * <p><b>变异验证</b>：把 {@code DefaultFeignConfig#requestInterceptor} 里注入该头的那行注释掉，
 * 本测试立刻红（下游会生成新 UUID，{@code traceId} 与 {@value #TRACE_ID} 不等）。
 */
@DisplayName("X-Trace-Id 两跳链路 — 出站头 → 下游 MDC → 内部错误体 → 解码器")
class TraceIdPropagationChainTest {

    /** 调用方 MDC 里的 traceId（相当于网关透传给第一跳的值）。 */
    private static final String TRACE_ID = "trace-hop-1";

    private static final String USER_SERVICE_URL = "http://user-service";

    /** 被测的 Feign 客户端形状（只要能发出一次 GET 即可）。 */
    public interface DemoUserClient {
        @RequestLine("GET /internal/user/1")
        String getUser();
    }

    @AfterEach
    void clearContext() {
        MDC.remove(TraceIdFilter.TRACE_ID);
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    @DisplayName("★ 两跳 — 调用方 MDC 的 traceId 原样出现在下游错误体与解码结果里")
    void shouldPropagateTraceIdAcrossTwoHops() {
        DownstreamService downstream = new DownstreamService();
        DemoUserClient client = newClient(downstream);

        // 第一跳：TraceIdFilter 已经把网关 X-Trace-Id 写进 MDC
        MDC.put(TraceIdFilter.TRACE_ID, TRACE_ID);

        assertThatThrownBy(client::getUser)
                .isInstanceOf(UpstreamException.class)
                .satisfies(thrown -> {
                    UpstreamException upstream = (UpstreamException) thrown;
                    assertThat(upstream.getUpstreamStatus()).isEqualTo(500);
                    assertThat(upstream.getCategory()).isEqualTo(ErrorCategory.BUG);
                    assertThat(upstream.isRetryable()).isFalse();
                    assertThat(upstream.getTraceId())
                            .as("下游 /internal/** 错误体的 traceId 必须等于调用方的 traceId"
                                    + "（否则跨服务回捞日志失效）")
                            .isEqualTo(TRACE_ID);
                });

        assertThat(downstream.receivedTraceId)
                .as("出站请求必须真的带上 X-Trace-Id")
                .isEqualTo(TRACE_ID);
        assertThat(downstream.downstreamMdcTraceId)
                .as("下游 TraceIdFilter 必须把该头写进 MDC（第二跳才有值）")
                .isEqualTo(TRACE_ID);
    }

    @Test
    @DisplayName("对照 — 调用方无 MDC 时下游自行生成 traceId（证明「同值」不是巧合）")
    void shouldLetDownstreamGenerateTraceId_whenCallerHasNoMdc() {
        DownstreamService downstream = new DownstreamService();
        DemoUserClient client = newClient(downstream);

        MDC.remove(TraceIdFilter.TRACE_ID);

        assertThatThrownBy(client::getUser)
                .isInstanceOf(UpstreamException.class)
                .satisfies(thrown -> assertThat(((UpstreamException) thrown).getTraceId())
                        .as("下游自己生成的追踪号也应非空（错误体键集合稳定）")
                        .isNotBlank()
                        .isNotEqualTo(TRACE_ID));

        assertThat(downstream.receivedTraceId)
                .as("MDC 无值时不得注入（更不能造假值）")
                .isNull();
        assertThat(downstream.downstreamMdcTraceId).isNotBlank().isNotEqualTo(TRACE_ID);
    }

    /** 用共享配置类里<b>真实</b>的拦截器与解码器装配一个 Feign 客户端（网络替换为进程内下游）。 */
    private static DemoUserClient newClient(DownstreamService downstream) {
        return Feign.builder()
                .requestInterceptor(new DefaultFeignConfig().requestInterceptor())
                .client(downstream)
                .errorDecoder(new UpstreamErrorDecoder())
                .target(DemoUserClient.class, USER_SERVICE_URL);
    }

    /**
     * 第二跳的进程内替身：只替代「网络」，服务侧行为走真实组件
     * （{@link TraceIdFilter} + {@link GlobalExceptionHandler}）。
     */
    private static final class DownstreamService implements Client {

        private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

        /** 下游实际收到的 {@code X-Trace-Id}（未收到则为 {@code null}）。 */
        private String receivedTraceId;

        /** 下游 {@code TraceIdFilter} 写进 MDC 的值（在过滤链内部捕获，出链后已被清理）。 */
        private String downstreamMdcTraceId;

        private String bodyJson;
        private int status;

        @Override
        public Response execute(Request request, Request.Options options) throws IOException {
            Collection<String> values = request.headers().get(SecurityHeaders.X_TRACE_ID);
            receivedTraceId = values == null || values.isEmpty() ? null : values.iterator().next();

            MockHttpServletRequest servletRequest = new MockHttpServletRequest("GET", "/internal/user/1");
            if (receivedTraceId != null) {
                servletRequest.addHeader(SecurityHeaders.X_TRACE_ID, receivedTraceId);
            }
            MockHttpServletResponse servletResponse = new MockHttpServletResponse();

            try {
                new TraceIdFilter().doFilter(servletRequest, servletResponse, (req, res) -> {
                    downstreamMdcTraceId = MDC.get(TraceIdFilter.TRACE_ID);
                    // /internal/** 的真实错误出口：真实状态码 + ErrorInfo（traceId 取自 MDC）
                    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(servletRequest));
                    ResponseEntity<?> entity = (ResponseEntity<?>) handler.handleBizException(
                            new BizException(ErrorCode.INTERNAL_ERROR));
                    status = entity.getStatusCode().value();
                    bodyJson = new ObjectMapper().writeValueAsString((ErrorInfo) entity.getBody());
                });
            } catch (Exception ex) {
                throw new IllegalStateException("下游替身执行失败", ex);
            } finally {
                RequestContextHolder.resetRequestAttributes();
            }

            return Response.builder()
                    .status(status)
                    .reason("Internal Server Error")
                    .headers(Map.of())
                    .request(request)
                    .body(bodyJson, StandardCharsets.UTF_8)
                    .build();
        }
    }
}
