package org.icedamericanomall.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.icedamericanomall.common.enums.ErrorCategory;
import org.icedamericanomall.common.enums.ErrorCode;

import java.io.Serializable;
import java.util.Objects;

/**
 * <b>内部接口</b>（{@code /internal/**}）的错误体 —— 让调用方足以判定「怎么处置」，而不是靠猜状态码。
 *
 * <p><b>适用范围（硬边界）</b>：只有 {@code /internal/**} 用它。对外 {@code /api/**} 的
 * 错误响应<b>仍然是也不得不是</b> {@link Result}（{@code {code,msg,data}}）—— 前端契约零变化
 * （{@code collaboration/09-constraints.md} §三·七·1「对外响应一律 {@code Result<T>} 信封」）。
 *
 * <p><b>字段口径</b>（§三·八·3「错误体必须足以判定处置」）：
 * <table border="1">
 *   <caption>字段含义</caption>
 *   <tr><th>字段</th><th>含义</th><th>来源</th></tr>
 *   <tr><td>{@code code}</td><td>业务码（{@link ErrorCode#getCode()}）</td><td>抛出的异常</td></tr>
 *   <tr><td>{@code msg}</td><td>已脱敏的展示文案</td><td>{@code CommonException#getDisplayMessage()}</td></tr>
 *   <tr><td>{@code category}</td><td>处置类别</td><td>{@link ErrorCategory#of(ErrorCode)}（<b>派生</b>，不手填）</td></tr>
 *   <tr><td>{@code retryable}</td><td>调用方是否可重试</td><td>{@link ErrorCategory#isRetryable()}（<b>派生</b>）</td></tr>
 *   <tr><td>{@code traceId}</td><td>全链路追踪 ID</td><td>网关 {@code X-Trace-Id} → {@code TraceIdFilter} → MDC</td></tr>
 * </table>
 *
 * <p><b>三个刻意的设计决定</b>：
 * <ol>
 *   <li><b>线上键用 {@code @JsonProperty} 逐字钉死</b>：各服务的 Jackson 命名策略并不统一
 *       （部分服务 {@code SNAKE_CASE}、部分未配置）。若依赖字段名隐式映射，
 *       {@code traceId} 会在某些服务里变成 {@code trace_id}，Feign 调用方解析失败后
 *       <b>静默降级为「未知」</b>——透传铁律就此失效。显式命名在两种策略下都恒为
 *       {@code code / msg / category / retryable / traceId}。</li>
 *   <li><b>{@code @JsonInclude(ALWAYS)}</b>：部分服务全局配置了 {@code non_null}，会让
 *       「取不到 traceId」时整键消失。内部错误体的<b>键集合必须稳定</b>（调用方按固定键解析），
 *       故此处强制包含 {@code null} 值。</li>
 *   <li><b>不可变 record</b>：错误体是值对象，构造即定型；{@code retryable} 与 {@code category}
 *       永远自洽（{@code retryable = (category == TRANSIENT)}），不提供可改的 setter。</li>
 * </ol>
 *
 * @param code      业务码
 * @param msg       展示文案（已脱敏）
 * @param category  处置类别
 * @param retryable 调用方是否可重试（恒等于 {@code category == TRANSIENT}）
 * @param traceId   全链路追踪 ID；取不到时为 {@code null}（<b>绝不</b>因此抛异常）
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record ErrorInfo(
        @JsonProperty("code") int code,
        @JsonProperty("msg") String msg,
        @JsonProperty("category") ErrorCategory category,
        @JsonProperty("retryable") boolean retryable,
        @JsonProperty("traceId") String traceId) implements Serializable {

    /** 序列化版本（值对象会随 {@code UpstreamException} 一起被序列化，故显式声明）。 */
    private static final long serialVersionUID = 1L;

    /**
     * 由错误码构造（类别与可重试性一律<b>派生</b>，不接受手填）。
     *
     * @param errorCode 错误码
     * @param msg       展示文案（已脱敏）
     * @param traceId   全链路追踪 ID，可为 {@code null}
     * @return 错误体
     */
    public static ErrorInfo of(ErrorCode errorCode, String msg, String traceId) {
        Objects.requireNonNull(errorCode, "errorCode");
        return of(errorCode.getCode(), msg, ErrorCategory.of(errorCode), traceId);
    }

    /**
     * 由「业务码 + 已定类别」构造（{@code UpstreamErrorDecoder} 解析下游报文时使用：
     * 业务码可能来自下游的枚举，本域未必有同名项）。
     *
     * @param code     业务码
     * @param msg      展示文案（已脱敏）
     * @param category 处置类别；{@code null} 按 {@link ErrorCategory#TRANSIENT} 处理
     * @param traceId  全链路追踪 ID，可为 {@code null}
     * @return 错误体（{@code retryable} 由 category 派生，入参无法覆盖）
     */
    public static ErrorInfo of(int code, String msg, ErrorCategory category, String traceId) {
        ErrorCategory resolved = category == null ? ErrorCategory.TRANSIENT : category;
        return new ErrorInfo(code, msg, resolved, resolved.isRetryable(), traceId);
    }
}
