package org.icedamericanomall.exception;

import feign.Response;
import org.icedamericanomall.common.enums.ErrorCategory;
import org.icedamericanomall.common.enums.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link UpstreamErrorDecoder} —— 非 2xx 响应 → {@link UpstreamException} 的解析与降级规则。
 *
 * <p>锁死 {@code collaboration/09-constraints.md} §三·八 的两条硬要求：
 * <ol>
 *   <li><b>4xx + 已知 code 原样保留</b>（业务码/文案不改写），并给出可判定处置的
 *       {@code category / retryable / traceId}；</li>
 *   <li><b>解析失败/非 JSON/无 code 时降级</b>为 {@code TRANSIENT + retryable=true} ——
 *       未知按可重试处理，<b>绝不</b>当成功。</li>
 * </ol>
 */
@DisplayName("UpstreamErrorDecoder — 非 2xx → UpstreamException（透传 / 降级）")
class UpstreamErrorDecoderTest {

    private static final String METHOD_KEY = "SpuDetailClient#getSpuDetail(String)";

    private static final String SPU_URL = "http://item-service/internal/item/spu/0f1e2d3c4b5a69788796a5b4c3d2e1f0";

    private final UpstreamErrorDecoder decoder = new UpstreamErrorDecoder();

    /** 已类型化的下游 404（本批新增错误体形态）。 */
    private static final String TYPED_404 = "{\"code\":1103,\"msg\":\"商品不存在或已删除\","
            + "\"category\":\"CLIENT\",\"retryable\":false,\"traceId\":\"trace-down-1\"}";

    private static UpstreamException decode(int status, String body) {
        return (UpstreamException) new UpstreamErrorDecoder().decode(METHOD_KEY, response(status, body));
    }

    private static Response response(int status, String body) {
        Response.Builder builder = Response.builder()
                .status(status)
                .reason("reason-" + status)
                .headers(Map.of())
                .request(originalRequest());
        return body == null ? builder.build() : builder.body(body, StandardCharsets.UTF_8).build();
    }

    /** feign 的 Response 必须带原始请求（否则 build() 抛「original request is required」）。 */
    private static feign.Request originalRequest() {
        return feign.Request.create(feign.Request.HttpMethod.GET, SPU_URL,
                Map.of(), (byte[]) null, StandardCharsets.UTF_8, null);
    }

    // ==================== 透传：下游已类型化 ====================

    @Test
    @DisplayName("404 + {code:1103,category:CLIENT} → 原样保留 code/文案，category=CLIENT，不可重试")
    void shouldPreserveClientErrorVerbatim() {
        UpstreamException ex = decode(404, TYPED_404);

        assertThat(ex.getCode()).isEqualTo(1103);
        assertThat(ex.getMsg()).isEqualTo("商品不存在或已删除");
        assertThat(ex.getCategory()).isEqualTo(ErrorCategory.CLIENT);
        assertThat(ex.isClientError()).isTrue();
        assertThat(ex.isRetryable()).isFalse();
        assertThat(ex.getTraceId()).isEqualTo("trace-down-1");
        assertThat(ex.getUpstreamStatus()).isEqualTo(404);
    }

    @Test
    @DisplayName("400 → CLIENT；422/409 等 4xx 一律 CLIENT（调用方的错）")
    void shouldMapAny4xxToClient() {
        assertThat(decode(400, "{\"code\":1101,\"msg\":\"参数错误\"}").getCategory())
                .isEqualTo(ErrorCategory.CLIENT);
        assertThat(decode(409, "{\"code\":3042,\"msg\":\"冲突\"}").getCategory())
                .isEqualTo(ErrorCategory.CLIENT);
        assertThat(decode(422, "{\"code\":1101,\"msg\":\"语义错误\"}").getCategory())
                .isEqualTo(ErrorCategory.CLIENT);
    }

    @Test
    @DisplayName("混合部署期 — 下游未类型化（旧 {code,msg}）时按状态码派生：404→CLIENT，500→BUG")
    void shouldDeriveCategoryFromStatus_whenDownstreamNotTyped() {
        UpstreamException notFound = decode(404, "{\"code\":1103,\"msg\":\"静态资源不存在\"}");
        assertThat(notFound.getCategory()).isEqualTo(ErrorCategory.CLIENT);
        assertThat(notFound.isClientError()).isTrue();

        UpstreamException serverError = decode(500, "{\"code\":1000,\"msg\":\"服务器内部错误\"}");
        assertThat(serverError.getCategory()).isEqualTo(ErrorCategory.BUG);
        assertThat(serverError.isRetryable()).isFalse();
        assertThat(serverError.getCode()).isEqualTo(1000);
    }

    @Test
    @DisplayName("下游类别优先 — 500 但下游标 TRANSIENT（上游不可用）→ TRANSIENT + 可重试")
    void shouldTrustDownstreamCategory_whenProvided() {
        UpstreamException ex = decode(500,
                "{\"code\":4008,\"msg\":\"第三方未知错误\",\"category\":\"TRANSIENT\",\"traceId\":\"t-9\"}");
        assertThat(ex.getCategory()).isEqualTo(ErrorCategory.TRANSIENT);
        assertThat(ex.isRetryable()).isTrue();
        assertThat(ex.getTraceId()).isEqualTo("t-9");
    }

    @Test
    @DisplayName("retryable 只由 category 派生 — 下游写 {category:CLIENT, retryable:true} 也判不可重试")
    void shouldReDeriveRetryable_whenDownstreamContradicts() {
        UpstreamException ex = decode(404,
                "{\"code\":1103,\"msg\":\"商品不存在\",\"category\":\"CLIENT\",\"retryable\":true}");
        assertThat(ex.getCategory()).isEqualTo(ErrorCategory.CLIENT);
        assertThat(ex.isRetryable()).isFalse();
    }

    @Test
    @DisplayName("traceId 兼容 snake 变体 trace_id（各服务命名策略不一致时的兜底）")
    void shouldReadSnakeCaseTraceId() {
        UpstreamException ex = decode(404, "{\"code\":1103,\"msg\":\"x\",\"trace_id\":\"trace-snake\"}");
        assertThat(ex.getTraceId()).isEqualTo("trace-snake");
    }

    @Test
    @DisplayName("非法 category 值 → 回落状态码派生（不让一个拼错的字符串毁掉整条判定）")
    void shouldIgnoreInvalidCategory() {
        UpstreamException ex = decode(404, "{\"code\":1103,\"msg\":\"x\",\"category\":\"NOT_A_CATEGORY\"}");
        assertThat(ex.getCategory()).isEqualTo(ErrorCategory.CLIENT);
    }

    // ==================== 降级：未知一律可重试 ====================

    @Test
    @DisplayName("空体（如网关 503 无 body）→ TRANSIENT + retryable=true，绝不当成功")
    void shouldDegradeToTransient_whenBodyMissing() {
        assertDegraded(decode(503, null));
        assertDegraded(decode(502, ""));
        assertDegraded(decode(500, "   "));
    }

    @Test
    @DisplayName("非 JSON（如 Tomcat HTML 错误页）→ TRANSIENT + retryable=true")
    void shouldDegradeToTransient_whenBodyNotJson() {
        assertDegraded(decode(500, "<html><body>Internal Server Error</body></html>"));
    }

    @Test
    @DisplayName("JSON 但无 code（契约外报文）→ TRANSIENT + retryable=true")
    void shouldDegradeToTransient_whenNoBusinessCode() {
        assertDegraded(decode(400, "{\"msg\":\"bad request\"}"));
        assertDegraded(decode(400, "{\"code\":null}"));
        assertDegraded(decode(400, "[1,2,3]"));
    }

    @Test
    @DisplayName("降级结果 — 业务码取本域 SYSTEM_BUSY(1001)，文案只说「无法解析」（不泄露原始报文）")
    void shouldUseSystemBusyCode_whenDegraded() {
        UpstreamException ex = decode(503, null);
        assertThat(ex.getCode()).isEqualTo(ErrorCode.SYSTEM_BUSY.getCode());
        assertThat(ex.getMsg()).contains("无法解析").contains("503");
        assertThat(ex.getTraceId()).isNull();
        assertThat(ex.getUpstreamStatus()).isEqualTo(503);
    }

    private static void assertDegraded(UpstreamException ex) {
        assertThat(ex.getCategory()).isEqualTo(ErrorCategory.TRANSIENT);
        assertThat(ex.isRetryable()).isTrue();
    }

    @Test
    @DisplayName("异常 message 含处置要素（类别/重试/traceId），便于日志定位")
    void shouldCarryDiagnosticMessage() {
        assertThat(decode(404, TYPED_404).getMessage())
                .contains("upstreamStatus=404").contains("code=1103")
                .contains("category=CLIENT").contains("retryable=false")
                .contains("traceId=trace-down-1");
    }
}
