package org.icedamericanomall.fallback;

import feign.Response;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.icedamericanomall.client.SpuDetailClient;
import org.icedamericanomall.common.enums.ErrorCategory;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.exception.UpstreamErrorDecoder;
import org.icedamericanomall.exception.UpstreamException;
import org.icedamericanomall.exception.UpstreamFailures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ConnectException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * <b>★ 批次 ⑤ 的验收核心</b>：{@code SpuDetailClient} 的「过度翻译」必须消失。
 *
 * <p><b>改前行为（缺陷）</b>：下游 404「商品不存在」→ Feign {@code FeignException} →
 * 降级工厂无条件 {@code BizException(SYSTEM_BUSY, "商品服务暂不可用")}。于是
 * {@code ai-service SpuDetailTool} 把「服务故障」的措辞喂给 LLM，用户看到「商品服务暂不可用」，
 * 而真实事实是「这个商品不存在」。
 *
 * <p><b>改后行为</b>（{@code collaboration/09-constraints.md} §三·八·1「4xx 透传」）：
 * 下游 4xx + 已知 code → 原样透传 {@link UpstreamException}（业务码 1103、类别 CLIENT、
 * 原始文案一字不改）；5xx / 429 / 超时 / 熔断 → 仍降级为 {@code SYSTEM_BUSY}，
 * 文案按 §三·八·2 改为「暂时无法获取」而非「服务不可用」。
 *
 * <p>本测试走的是<b>真实链条</b>：{@link UpstreamErrorDecoder} 解出 {@link UpstreamException}
 * （Feign 在非 2xx 时的真实产物），再喂给降级工厂 —— 而不是手工 new 一个异常，
 * 这样才能证明「解码 → 降级」两段拼起来语义正确。
 */
@DisplayName("SpuDetailClientFallback — 4xx 透传 / 5xx·超时·熔断降级（§三·八）")
class SpuDetailClientFallbackTest {

    private static final String SPU_ID = "0f1e2d3c4b5a69788796a5b4c3d2e1f0";

    private static final String TYPED_404 = "{\"code\":1103,\"msg\":\"商品不存在或已删除\","
            + "\"category\":\"CLIENT\",\"retryable\":false,\"traceId\":\"trace-down-1\"}";

    private final SpuDetailClientFallback fallbackFactory = new SpuDetailClientFallback();

    /** 模拟 Feign 的真实产物：非 2xx → UpstreamErrorDecoder → UpstreamException。 */
    private static UpstreamException decodeFailure(int status, String body) {
        Response.Builder builder = Response.builder()
                .status(status)
                .reason("r" + status)
                .headers(Map.of())
                .request(feign.Request.create(feign.Request.HttpMethod.GET,
                        "http://item-service/internal/item/spu/" + SPU_ID,
                        Map.of(), (byte[]) null, StandardCharsets.UTF_8, null));
        Response response = body == null ? builder.build() : builder.body(body, StandardCharsets.UTF_8).build();
        return (UpstreamException) new UpstreamErrorDecoder()
                .decode("SpuDetailClient#getSpuDetail(String)", response);
    }

    private SpuDetailClient degradedClient(Throwable cause) {
        return fallbackFactory.create(cause);
    }

    // ==================== ★ 核心：404 必须透传，不得翻译成 SYSTEM_BUSY ====================

    @Test
    @DisplayName("★ 下游 404 + {code:1103,category:CLIENT} → 抛 UpstreamException(CLIENT,1103)，而非 SYSTEM_BUSY")
    void shouldPassThroughClientError_whenDownstreamSaysNotFound() {
        SpuDetailClient client = degradedClient(decodeFailure(404, TYPED_404));

        assertThatThrownBy(() -> client.getSpuDetail(SPU_ID))
                .isInstanceOf(UpstreamException.class)
                .isNotInstanceOf(BizException.class)
                .satisfies(thrown -> {
                    UpstreamException upstream = (UpstreamException) thrown;
                    assertThat(upstream.getCode()).isEqualTo(1103);
                    assertThat(upstream.getCategory()).isEqualTo(ErrorCategory.CLIENT);
                    assertThat(upstream.isClientError()).isTrue();
                    assertThat(upstream.isRetryable()).isFalse();
                    // 原始文案与 traceId 一字不改（透传的含义）
                    assertThat(upstream.getMsg()).isEqualTo("商品不存在或已删除");
                    assertThat(upstream.getTraceId()).isEqualTo("trace-down-1");
                    assertThat(upstream.getUpstreamStatus()).isEqualTo(404);
                });
    }

    @Test
    @DisplayName("★ 透传不会被包装成「服务不可用」—— 异常 message 里不得出现 SYSTEM_BUSY 语义")
    void shouldNotMassageClientErrorIntoSystemBusy() {
        SpuDetailClient client = degradedClient(decodeFailure(404, TYPED_404));

        assertThatThrownBy(() -> client.getSpuDetail(SPU_ID))
                .hasMessageNotContaining("暂不可用")
                .hasMessageNotContaining("请稍后重试")
                .hasMessageContaining("code=1103");
    }

    @Test
    @DisplayName("400（业务键形态非法 / 参数错）同样透传 —— 调用方拿到的仍是 CLIENT")
    void shouldPassThroughClientError_whenDownstreamSaysBadRequest() {
        SpuDetailClient client = degradedClient(
                decodeFailure(400, "{\"code\":1101,\"msg\":\"参数错误: spuId 非 32-hex\"}"));

        assertThatThrownBy(() -> client.getSpuDetail("bad-id"))
                .isInstanceOf(UpstreamException.class)
                .satisfies(thrown -> {
                    UpstreamException upstream = (UpstreamException) thrown;
                    assertThat(upstream.getCode()).isEqualTo(1101);
                    assertThat(upstream.getCategory()).isEqualTo(ErrorCategory.CLIENT);
                });
    }

    // ==================== 真正需要降级的路径仍降级 ====================

    @Test
    @DisplayName("下游 503 → 降级 BizException(SYSTEM_BUSY)，文案为「暂时无法获取」（不得写「服务不可用」）")
    void shouldDegradeToSystemBusy_whenDownstreamUnavailable() {
        SpuDetailClient client = degradedClient(
                decodeFailure(503, "{\"code\":1001,\"msg\":\"系统繁忙\",\"category\":\"TRANSIENT\"}"));

        assertThatThrownBy(() -> client.getSpuDetail(SPU_ID))
                .isInstanceOf(BizException.class)
                .isNotInstanceOf(UpstreamException.class)
                .hasMessageContaining("暂时无法获取")
                .hasMessageNotContaining("服务不可用");
    }

    @Test
    @DisplayName("下游 500（BUG 类）→ 仍降级 SYSTEM_BUSY（代码缺陷不向调用方暴露细节）")
    void shouldDegradeToSystemBusy_whenDownstreamBug() {
        SpuDetailClient client = degradedClient(
                decodeFailure(500, "{\"code\":1000,\"msg\":\"服务器内部错误\",\"category\":\"BUG\"}"));

        assertThatThrownBy(() -> client.getSpuDetail(SPU_ID))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("暂时无法获取");
    }

    @Test
    @DisplayName("超时/连接失败（拿不到 UpstreamException）→ 降级 SYSTEM_BUSY（fail-closed，不得返回 null）")
    void shouldDegradeToSystemBusy_whenTimeoutWithoutUpstreamBody() {
        SpuDetailClient client = degradedClient(new RuntimeException(new IOException("Read timed out")));

        assertThatThrownBy(() -> client.getSpuDetail(SPU_ID))
                .isInstanceOf(BizException.class)
                .satisfies(thrown -> assertThat(((BizException) thrown).getErrorCode())
                        .isEqualTo(ErrorCode.SYSTEM_BUSY));
    }

    /**
     * ★ 批次 ⑤b：<b>熔断打开</b>在 Feign 侧没有响应体，只有异常。
     *
     * <p><b>锁死的缺陷（修复前这条断言无从表达）</b>：降级工厂此前只判
     * {@code upstream.isClientError()}，「熔断打开判 TRANSIENT」只是靠「不是 CLIENT 就降级」
     * 的排除法<b>顺带</b>成立 —— 判据本身说不清。改用统一入口
     * （{@link org.icedamericanomall.exception.UpstreamFailures#classify}）后，
     * 「熔断 → TRANSIENT 且可重试」是被<b>显式</b>判定的，同时降级仍是 fail-closed
     * （展示型数据不许伪装成「成功但没数据」）。
     */
    @Test
    @DisplayName("熔断打开（CallNotPermittedException）→ 判 TRANSIENT + 可重试，且仍 fail-closed 降级")
    void shouldDegrade_whenCircuitBreakerOpen() {
        CallNotPermittedException circuitOpen = CallNotPermittedException
                .createCallNotPermittedException(CircuitBreaker.ofDefaults("spuDetail"));

        assertThat(UpstreamFailures.classify(circuitOpen)).isEqualTo(ErrorCategory.TRANSIENT);
        // ★ 判别性断言：按状态码 500 派生会是 BUG（不可重试）—— 熔断必须赢，否则重试被堵死
        assertThat(UpstreamFailures.classify(500, circuitOpen)).isEqualTo(ErrorCategory.TRANSIENT);
        assertThat(UpstreamFailures.classify(circuitOpen).isRetryable()).isTrue();

        SpuDetailClient client = degradedClient(new RuntimeException("circuit-breaker decorator", circuitOpen));

        assertThatThrownBy(() -> client.getSpuDetail(SPU_ID))
                .isInstanceOf(BizException.class)
                .isNotInstanceOf(UpstreamException.class)
                .hasMessageContaining("暂时无法获取");
    }

    @Test
    @DisplayName("连接失败（ConnectException，无响应体）→ 判 TRANSIENT，降级文案仍是「暂时无法获取」")
    void shouldDegrade_whenConnectRefused() {
        ConnectException refused = new ConnectException("Connection refused");

        assertThat(UpstreamFailures.classify(refused)).isEqualTo(ErrorCategory.TRANSIENT);

        assertThatThrownBy(() -> degradedClient(refused).getSpuDetail(SPU_ID))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("暂时无法获取");
    }

    @Test
    @DisplayName("熔断器/装饰器再包一层时仍能识别上游语义（按异常链回溯，不只看第一层）")
    void shouldFindUpstreamExceptionInCauseChain() {
        UpstreamException origin = decodeFailure(404, TYPED_404);
        SpuDetailClient client = degradedClient(new RuntimeException("wrapped by decorator", origin));

        assertThatThrownBy(() -> client.getSpuDetail(SPU_ID))
                .isInstanceOf(UpstreamException.class)
                .satisfies(thrown -> assertThat(((UpstreamException) thrown).getCode()).isEqualTo(1103));
    }

    @Test
    @DisplayName("null cause（防御性）→ 降级 SYSTEM_BUSY，绝不 NPE、绝不返回 null")
    void shouldDegradeToSystemBusy_whenCauseIsNull() {
        SpuDetailClient client = degradedClient(null);

        assertThatThrownBy(() -> client.getSpuDetail(SPU_ID))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("暂时无法获取");
    }
}
