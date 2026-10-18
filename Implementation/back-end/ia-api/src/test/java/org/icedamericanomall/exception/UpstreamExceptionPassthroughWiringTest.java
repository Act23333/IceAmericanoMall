package org.icedamericanomall.exception;

import feign.Request;
import feign.Response;
import org.icedamericanomall.common.enums.ErrorCategory;
import org.icedamericanomall.common.exception.CarriedErrorException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * <b>接缝守卫</b>：{@link UpstreamException} 与 {@link CarriedErrorException} 的继承关系必须成立。
 *
 * <h2>这条断言守的是什么</h2>
 * {@code ia-common} 的 {@code GlobalExceptionHandler} 靠 {@link CarriedErrorException} 这个
 * <b>形状</b>来选中「透传分支」（见该 handler 的 {@code @ExceptionHandler}）。而
 * {@code ia-common} <b>不能</b>依赖 {@code ia-common} → {@code ia-api}（会成环），
 * 所以「{@code UpstreamException} 是不是这个形状」这件事，<b>没有任何编译器或跨模块检查能保证</b>：
 * <ul>
 *   <li>把 {@code UpstreamException} 改回 {@code extends RuntimeException} —— 全仓<b>编译依旧通过</b>，
 *       ia-common 的测试也<b>依旧全绿</b>（那里用的是合成子类，不是真实类）；</li>
 *   <li>但运行期 {@code @ExceptionHandler(CarriedErrorException.class)} 选不中它，
 *       一切上游 4xx 又会被兜底成 HTTP 500 —— 即「透传在最后一米失效」这个缺陷原样复发，
 *       而且<b>没有任何红</b>。</li>
 * </ul>
 * 本条用例就是补齐这个缺口的那一颗钉子：它站在「A 交给 B」那一行上
 * （{@code 09-constraints} 的接缝原则）。放在 {@code ia-api} 侧，是因为只有这里同时看得见
 * 两个类。
 */
@DisplayName("接缝守卫 — UpstreamException 必须是 CarriedErrorException（否则 4xx 透传静默失效）")
class UpstreamExceptionPassthroughWiringTest {

    /** 下游错误体范文（4xx + 已类型化错误体）。 */
    private static final String TYPED_404 = "{\"code\":1103,\"msg\":\"商品不存在或已删除\","
            + "\"category\":\"CLIENT\",\"retryable\":false,\"traceId\":\"trace-down-1\"}";

    private static final String TYPED_503 = "{\"code\":1001,\"msg\":\"系统繁忙\","
            + "\"category\":\"TRANSIENT\",\"retryable\":true,\"traceId\":\"trace-down-2\"}";

    @Test
    @DisplayName("★ UpstreamException 必须是 CarriedErrorException 的子类（改回 RuntimeException 即红）")
    void shouldExtendCarriedErrorException() {
        assertThat(CarriedErrorException.class.isAssignableFrom(UpstreamException.class))
                .as("UpstreamException 不再继承 CarriedErrorException ⇒ ia-common 的 "
                        + "@ExceptionHandler(CarriedErrorException.class) 选不中它，"
                        + "上游 4xx 会被兜底成 HTTP 500（透传在最后一米静默失效）")
                .isTrue();
    }

    @Test
    @DisplayName("★ 接缝闭环 — 真实解码出的 404 → CLIENT → httpStatus() 原样还原 404")
    void shouldRoundTripDownstreamStatus_whenDecodedAsClientError() {
        UpstreamException decoded = decode(404, TYPED_404);

        assertThat(decoded.getCategory()).isEqualTo(ErrorCategory.CLIENT);
        assertThat(decoded.getUpstreamStatus()).isEqualTo(404);
        // 状态码还原与业务码保留必须同时成立：一个还原错、一个被替换，都算透传失败
        assertThat(decoded.httpStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(decoded.getCode()).isEqualTo(1103);
        assertThat(decoded.getMsg()).isEqualTo("商品不存在或已删除");
        assertThat(decoded.isClientError()).isTrue();
    }

    @Test
    @DisplayName("不可用（503/TRANSIENT）→ httpStatus() 还原 503 且 retryable=true")
    void shouldRoundTripTransient_whenDecodedAsUnavailable() {
        UpstreamException decoded = decode(503, TYPED_503);

        assertThat(decoded.getCategory()).isEqualTo(ErrorCategory.TRANSIENT);
        assertThat(decoded.httpStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE.value());
        assertThat(decoded.isRetryable()).isTrue();
        assertThat(decoded.isClientError()).isFalse();
    }

    // ==================== findIn 语义未因换父类而改变 ====================

    @Test
    @DisplayName("findIn — 装饰器包装一层后仍能找到原实例（语义与换父类前一致）")
    void shouldFindWrappedException() {
        UpstreamException original = decode(404, TYPED_404);

        assertThat(UpstreamException.findIn(original)).isSameAs(original);
        assertThat(UpstreamException.findIn(new RuntimeException("decorator", original))).isSameAs(original);
        assertThat(UpstreamException.findIn(new IllegalStateException("无关异常"))).isNull();
        assertThat(UpstreamException.findIn(null)).isNull();
    }

    /** 用真实解码器造一枚上游异常（不手搓 {@code ErrorInfo}，否则接缝又变成「各喂一份自编数据」）。 */
    private UpstreamException decode(int status, String body) {
        Response response = Response.builder()
                .status(status)
                .reason("r" + status)
                .headers(Map.of())
                .request(Request.create(Request.HttpMethod.GET,
                        "http://downstream/internal/probe", Map.of(), (byte[]) null,
                        StandardCharsets.UTF_8, null))
                .body(body, StandardCharsets.UTF_8)
                .build();
        return (UpstreamException) new UpstreamErrorDecoder().decode("Probe#call()", response);
    }
}
