package org.icedamericanomall.exception;

import org.icedamericanomall.common.api.ErrorInfo;
import org.icedamericanomall.common.enums.ErrorCategory;

import java.util.Objects;

/**
 * <b>跨服务调用的统一失败载体</b>（全仓唯一 —— 禁止各服务自建 {@code XxxClientException}）。
 *
 * <p><b>它解决什么问题</b>：Feign 默认把一切非 2xx 响应折叠成 {@code FeignException} —— 只有
 * 状态码和一段原始报文，调用方<b>无法区分</b>「下游说这单不存在（4xx）」和「下游挂了（5xx）」，
 * 于是最容易的写法就是一律兜底 {@code SYSTEM_BUSY}，把业务事实说成服务故障。
 * {@code collaboration/09-constraints.md} §三·八·1 明确禁止这种改写：<b>下游 4xx + 已知 code
 * 必须原样透传，只有 5xx / 超时 / 熔断才允许改写成本域的 {@code SYSTEM_BUSY}</b>。
 * 本异常就是「透传所需的全部信息」的载体：原始业务码、原始文案、处置类别、可重试性、traceId、
 * 原始 HTTP 状态码。
 *
 * <p><b>三个字段的口径</b>：
 * <ul>
 *   <li>{@link #getCode()} / {@link #getMsg()}：<b>下游的</b>业务码与文案，<b>原样保留</b>
 *       （改写即丢失「是哪一个业务事实」）。</li>
 *   <li>{@link #getCategory()} / {@link #isRetryable()}：来自下游错误体的
 *       {@code category}（下游未类型化时由 HTTP 状态码派生，见
 *       {@link org.icedamericanomall.common.enums.ErrorCategory#ofStatus(int)}）。
 *       调用方据此决定「透传 / 重试 / 告警」，<b>不得</b>再凭状态码猜测。</li>
 *   <li>{@link #getTraceId()}：下游请求的 traceId（链路追踪，可为 {@code null}）；
 *       {@link #getUpstreamStatus()}：下游真实 HTTP 状态码。</li>
 * </ul>
 *
 * <p><b>为什么继承 {@code RuntimeException} 而不是 {@code CommonException}：</b>
 * {@code CommonException} 强制绑定一个本域的 {@link org.icedamericanomall.common.enums.ErrorCode}
 * —— 而下游的业务码（如 1103）在本域未必有同名枚举项，硬套会逼出
 * 「用自己的码替代下游码」的改写，正是 §三·八 要禁止的。故本异常自带
 * {@link ErrorInfo}，不依赖本域枚举。
 *
 * <p><b>降级语义（重要）</b>：调用方的 fallback 工厂<u>不得</u>无条件把它换成
 * {@code SYSTEM_BUSY}。正确做法见 {@code SpuDetailClientFallback}：
 * {@code category == CLIENT} → <b>原样抛出本异常</b>；其余（{@code TRANSIENT} / {@code BUG}）
 * 才按本域口径降级。
 */
public class UpstreamException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** 异常链回溯上限 —— 防止畸形/自引用链把降级路径拖成死循环。 */
    private static final int MAX_CAUSE_DEPTH = 16;

    /** 下游错误体（业务码 / 文案 / 类别 / 可重试性 / traceId）。 */
    private final ErrorInfo errorInfo;

    /** 下游真实 HTTP 状态码（0 表示未知，例如连接层失败）。 */
    private final int upstreamStatus;

    /**
     * @param errorInfo      下游错误体（不可为 {@code null}）
     * @param upstreamStatus 下游真实 HTTP 状态码（未知时传 0）
     */
    public UpstreamException(ErrorInfo errorInfo, int upstreamStatus) {
        this(errorInfo, upstreamStatus, null);
    }

    /**
     * @param errorInfo      下游错误体（不可为 {@code null}）
     * @param upstreamStatus 下游真实 HTTP 状态码（未知时传 0）
     * @param cause          原始异常（可为 {@code null}）
     */
    public UpstreamException(ErrorInfo errorInfo, int upstreamStatus, Throwable cause) {
        super(buildMessage(errorInfo, upstreamStatus), cause);
        this.errorInfo = Objects.requireNonNull(errorInfo, "errorInfo 不可为 null");
        this.upstreamStatus = upstreamStatus;
    }

    /** @return 下游业务码（原样保留，不做本域映射） */
    public int getCode() {
        return errorInfo.code();
    }

    /** @return 下游展示文案（原样保留） */
    public String getMsg() {
        return errorInfo.msg();
    }

    /** @return 处置类别（CLIENT / RULE / TRANSIENT / BUG） */
    public ErrorCategory getCategory() {
        return errorInfo.category();
    }

    /** @return 调用方是否可重试（恒等于 {@code category == TRANSIENT}） */
    public boolean isRetryable() {
        return errorInfo.retryable();
    }

    /** @return 下游请求的 traceId，可为 {@code null} */
    public String getTraceId() {
        return errorInfo.traceId();
    }

    /** @return 下游真实 HTTP 状态码（0 表示未知） */
    public int getUpstreamStatus() {
        return upstreamStatus;
    }

    /** @return 下游错误体原件（需要把整包错误继续往上抛时使用） */
    public ErrorInfo getErrorInfo() {
        return errorInfo;
    }

    /**
     * 是否为「调用方的错」—— 按 {@code 09-constraints} §三·八·1 必须<b>原样透传</b>的那一类。
     *
     * @return {@code true} 表示 4xx 语义（参数 / 资源不存在 / 凭证 / 越权 / 冲突）
     */
    public boolean isClientError() {
        return getCategory() == ErrorCategory.CLIENT;
    }

    /**
     * 在异常链中查找上游错误 —— 熔断/降级/超时装饰器都可能再包一层，直接 {@code instanceof}
     * 判断第一层会漏掉真正的上游语义。
     *
     * @param cause 任意异常（可为 {@code null}）
     * @return 找到的 {@link UpstreamException}；不存在则 {@code null}
     */
    public static UpstreamException findIn(Throwable cause) {
        Throwable current = cause;
        for (int depth = 0; current != null && depth < MAX_CAUSE_DEPTH; depth++) {
            if (current instanceof UpstreamException upstream) {
                return upstream;
            }
            Throwable next = current.getCause();
            if (next == current) {
                return null;
            }
            current = next;
        }
        return null;
    }

    /**
     * 异常 message（只进日志/异常链，不作对外文案）。
     *
     * <p>刻意带上全部处置要素：故障现场最需要回答的是「哪个下游、什么类别、能不能重试、
     * 用哪个 traceId 去捞日志」。
     */
    private static String buildMessage(ErrorInfo errorInfo, int upstreamStatus) {
        if (errorInfo == null) {
            return "上游调用失败: 缺少错误体 (upstreamStatus=" + upstreamStatus + ")";
        }
        return "上游调用失败: upstreamStatus=" + upstreamStatus
                + ", code=" + errorInfo.code()
                + ", category=" + errorInfo.category()
                + ", retryable=" + errorInfo.retryable()
                + ", traceId=" + errorInfo.traceId()
                + ", msg=" + errorInfo.msg();
    }
}
