package org.icedamericanomall.exception;

import feign.Request;
import feign.RetryableException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.icedamericanomall.common.api.ErrorInfo;
import org.icedamericanomall.common.enums.ErrorCategory;
import org.icedamericanomall.common.enums.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link UpstreamFailures} —— 跨服务失败的<b>唯一类别判定入口</b>（解码路径 ∩ 降级路径）。
 *
 * <p>锁死 {@code collaboration/09-constraints.md} §三·八 的判定顺序：
 * <b>异常类型 → HTTP 状态码 → 500 才是 BUG</b>。
 *
 * <p><b>为什么这条测试必须同时覆盖两个方向</b>：只测「熔断 → TRANSIENT」会让实现退化成
 * 「一律 TRANSIENT」（真缺陷被当成抖动，无限重试且不告警）；只测「500 → BUG」会让实现退化成
 * 「一律按状态码」（熔断/超时没有响应可看，全部漏判）。两个方向都钉住，判据才是真的。
 *
 * <p>测试用的都是<b>真实库类</b>（{@code feign.RetryableException}、
 * {@code resilience4j} 的 {@code CallNotPermittedException}），不是同名的假类 ——
 * 分类器按「类型全名」比对（{@code ia-common} 不声明这两个依赖，见
 * {@code ErrorCategory#TRANSIENT_TYPE_NAMES}），用假类测不出「改名即失效」这类真实缺陷。
 */
@DisplayName("UpstreamFailures — 异常类型优先判定（解码 / 降级共用入口）")
class UpstreamFailuresTest {

    /** Feign 的 Response 必须带原始请求，故这里造一个只用于异常构造的请求对象。 */
    private static Request anyRequest() {
        return Request.create(Request.HttpMethod.GET, "http://user-service/internal/user/1",
                Map.of(), (byte[]) null, StandardCharsets.UTF_8, null);
    }

    private static UpstreamException upstream(ErrorInfo errorInfo, int status) {
        return new UpstreamException(errorInfo, status);
    }

    // ==================== 异常类型优先 ====================

    @Test
    @DisplayName("连接失败（ConnectException：没有 HTTP 响应）→ TRANSIENT 且可重试")
    void shouldClassifyConnectFailureAsTransient() {
        ConnectException refused = new ConnectException("Connection refused");

        assertThat(UpstreamFailures.classify(refused)).isEqualTo(ErrorCategory.TRANSIENT);
        assertThat(UpstreamFailures.classify(503, refused)).isEqualTo(ErrorCategory.TRANSIENT);
        // ★ 判别性断言：单纯「没有状态码 → 未知 → TRANSIENT」也能让上面两行通过，
        //   故必须给出「按状态码会判 BUG」的 500，才能证明**是异常类型**在起作用。
        assertThat(UpstreamFailures.classify(500, refused))
                .as("连接失败必须压过 500 派生出的 BUG（否则熔断/连接失败会被判成代码缺陷且不可重试）")
                .isEqualTo(ErrorCategory.TRANSIENT);
        assertThat(UpstreamFailures.classify(refused).isRetryable()).isTrue();
    }

    @Test
    @DisplayName("Feign 的 RetryableException（真实类，包装连接/超时）→ TRANSIENT 且可重试")
    void shouldClassifyFeignRetryableExceptionAsTransient() {
        RetryableException retryable = new RetryableException(500, "connect timeout",
                Request.HttpMethod.GET, (Throwable) null, (Long) null, anyRequest());

        assertThat(UpstreamFailures.classify(retryable)).isEqualTo(ErrorCategory.TRANSIENT);
        assertThat(UpstreamFailures.classify(500, retryable)).isEqualTo(ErrorCategory.TRANSIENT);
        assertThat(UpstreamFailures.classify(retryable).isRetryable()).isTrue();
    }

    @Test
    @DisplayName("超时（SocketTimeoutException / TimeoutException）→ TRANSIENT")
    void shouldClassifyTimeoutAsTransient() {
        assertThat(UpstreamFailures.classify(500, new SocketTimeoutException("Read timed out")))
                .isEqualTo(ErrorCategory.TRANSIENT);
        assertThat(UpstreamFailures.classify(500, new java.util.concurrent.TimeoutException("timed out")))
                .isEqualTo(ErrorCategory.TRANSIENT);
    }

    @Test
    @DisplayName("熔断打开（resilience4j CallNotPermittedException 真实类）→ TRANSIENT 且可重试")
    void shouldClassifyCircuitOpenAsTransient() {
        CallNotPermittedException circuitOpen = CallNotPermittedException
                .createCallNotPermittedException(CircuitBreaker.ofDefaults("spuDetail"));

        assertThat(UpstreamFailures.classify(circuitOpen)).isEqualTo(ErrorCategory.TRANSIENT);
        // ★ 判别性断言：按状态码 500 派生会是 BUG（不可重试）；熔断必须赢
        assertThat(UpstreamFailures.classify(500, circuitOpen))
                .as("熔断打开必须判 TRANSIENT（可重试），不能判 BUG（不可重试）")
                .isEqualTo(ErrorCategory.TRANSIENT);
        assertThat(UpstreamFailures.classify(circuitOpen).isRetryable()).isTrue();
        // 装饰器再包一层也要认出来（熔断打开几乎总是被 InvocationHandler 包过）
        assertThat(UpstreamFailures.classify(new RuntimeException("decorator", circuitOpen)))
                .isEqualTo(ErrorCategory.TRANSIENT);
    }

    @Test
    @DisplayName("异常类型优先于状态码 — 即便有 4xx 状态码线索，传输层失败仍判 TRANSIENT")
    void shouldPreferExceptionTypeOverHttpStatus() {
        // 记录判定顺序：类型 → 状态码。若实现把顺序颠倒（先看状态码），这里会得到一个 CLIENT。
        assertThat(UpstreamFailures.classify(404, new ConnectException("Connection refused")))
                .isEqualTo(ErrorCategory.TRANSIENT);
    }

    // ==================== 已类型化的上游错误体 ====================

    @Test
    @DisplayName("已类型化错误体（下游 404 + CLIENT）→ 原样采信 CLIENT（透传语义的基础）")
    void shouldTrustTypedUpstreamCategory() {
        UpstreamException notFound = upstream(
                ErrorInfo.of(ErrorCode.NO_RESOURCE_FOUND, "商品不存在", "trace-1"), 404);

        assertThat(UpstreamFailures.classify(notFound)).isEqualTo(ErrorCategory.CLIENT);
        assertThat(UpstreamFailures.classify(notFound).isRetryable()).isFalse();
        // 装饰器包装一层仍要认出来
        assertThat(UpstreamFailures.classify(new RuntimeException("decorator", notFound)))
                .isEqualTo(ErrorCategory.CLIENT);
    }

    @Test
    @DisplayName("已类型化错误体 — 下游 500 但自报 TRANSIENT 时，采信下游结论而非状态码")
    void shouldTrustDownstreamTransientOverStatus() {
        UpstreamException downstreamError = upstream(
                ErrorInfo.of(ErrorCode.THIRD_PARTY_UNDEFINED, "第三方未知错误", "trace-2"), 500);

        assertThat(downstreamError.getCategory()).isEqualTo(ErrorCategory.TRANSIENT);
        assertThat(UpstreamFailures.classify(downstreamError)).isEqualTo(ErrorCategory.TRANSIENT);
        // 同一请求若丢了错误体（只剩状态码 500），就必须回落到 BUG —— 这是两条判据的分界
        assertThat(UpstreamFailures.classify(500, null)).isEqualTo(ErrorCategory.BUG);
    }

    // ==================== 状态码兜底：500 才是 BUG ====================

    @Test
    @DisplayName("★ 500 且无异常线索 → BUG 且 retryable=false（防「一律 TRANSIENT」反向回归）")
    void shouldClassify500WithoutClueAsBug() {
        ErrorCategory category = UpstreamFailures.classify(500, null);

        assertThat(category).isEqualTo(ErrorCategory.BUG);
        assertThat(category.isRetryable()).isFalse();
        // 与 ia-common 的统一入口一致（不允许两条路径各判一套）
        assertThat(ErrorCategory.of(500, null)).isEqualTo(ErrorCategory.BUG);
        // 非网络类异常同样不能因为「有个异常」就洗成暂时性故障
        assertThat(UpstreamFailures.classify(500, new NullPointerException("npe")))
                .isEqualTo(ErrorCategory.BUG);
        assertThat(UpstreamFailures.classify(500, new IllegalStateException("契约违约")))
                .isEqualTo(ErrorCategory.BUG);
    }

    @Test
    @DisplayName("状态码兜底 — 4xx → CLIENT，2xx → RULE，408/429/5xx-infra → TRANSIENT")
    void shouldDeriveFromStatus_whenNoExceptionClue() {
        assertThat(UpstreamFailures.classify(400, null)).isEqualTo(ErrorCategory.CLIENT);
        assertThat(UpstreamFailures.classify(404, null)).isEqualTo(ErrorCategory.CLIENT);
        assertThat(UpstreamFailures.classify(200, null)).isEqualTo(ErrorCategory.RULE);
        assertThat(UpstreamFailures.classify(408, null)).isEqualTo(ErrorCategory.TRANSIENT);
        assertThat(UpstreamFailures.classify(429, null)).isEqualTo(ErrorCategory.TRANSIENT);
        assertThat(UpstreamFailures.classify(503, null)).isEqualTo(ErrorCategory.TRANSIENT);
    }

    @Test
    @DisplayName("未知 — 既无状态码也无异常类型 → TRANSIENT（未知按可重试处理，绝不当成功）")
    void shouldTreatUnknownAsTransient() {
        assertThat(UpstreamFailures.classify(null)).isEqualTo(ErrorCategory.TRANSIENT);
        assertThat(UpstreamFailures.classify(null, null)).isEqualTo(ErrorCategory.TRANSIENT);
        assertThat(UpstreamFailures.classify(new NullPointerException("npe")))
                .isEqualTo(ErrorCategory.TRANSIENT);
    }
}
