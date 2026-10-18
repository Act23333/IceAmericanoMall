package org.icedamericanomall.common.exception;

import org.icedamericanomall.common.api.ErrorInfo;
import org.icedamericanomall.common.enums.ErrorCategory;
import org.springframework.http.HttpStatus;

import java.util.Objects;

/**
 * <b>「已类型化上游错误」的载体基类</b> —— 让 {@code GlobalExceptionHandler} 能在
 * <b>不反向依赖 {@code ia-api}</b> 的前提下把下游失败原样透传到 HTTP 响应。
 *
 * <h2>为什么需要它（这是「4xx 透传」最后一步断链的根因）</h2>
 * {@code collaboration/09-constraints.md} §三·八·1 要求「下游 4xx + 已知 code 必须原样透传」。
 * 该要求此前只实现到<b>降级工厂</b>为止：30 个工厂里该透传的都已 {@code throw upstream}
 * （见 {@code org.icedamericanomall.fallback} 与两只门禁）。但异常抛出之后，
 * <b>没有任何 {@code @ExceptionHandler} 能接住它</b>：
 * <ul>
 *   <li>{@code ia-api} 的 {@code UpstreamException} 历史上<b>继承 {@code RuntimeException}</b>
 *       （因为它不绑定本域 {@code ErrorCode}），于是落到
 *       {@code GlobalExceptionHandler#handleException(Exception)} 兜底分支 →
 *       {@code INTERNAL_ERROR} / <b>HTTP 500</b>；</li>
 *   <li>于是「透传」的全部投资在最后一米失效：调用方拿到的仍是 500 ——
 *       既看不出「是这单不存在」，也会被上层当成服务故障重试。</li>
 * </ul>
 *
 * <p><b>为什么不在 {@code ia-common} 里直接 {@code import UpstreamException}</b>：
 * {@code ia-common} 是 {@code ia-api} 的<b>上游依赖</b>，反向引用会构成模块环
 * （{@code ErrorCategory} 的 javadoc 已把这条禁令写死：「{@code ia-common} 不能反向依赖
 * {@code ia-api}」）。故把「透传所需的形状」下沉到本类：{@link ErrorInfo} 本来就在
 * {@code ia-common}，载体只需再加一个「下游真实 HTTP 状态码」。
 *
 * <h2>分层关系（读代码时请记住这两层）</h2>
 * <table border="1">
 *   <caption>谁是什么</caption>
 *   <tr><th>类型</th><th>模块</th><th>角色</th></tr>
 *   <tr><td>{@code CarriedErrorException}（本类）</td><td>{@code ia-common}</td>
 *       <td><b>只说「我带着一份上游错误体」</b>；不绑定任何 Feign/HTTP 客户端概念，
 *           是被 {@code GlobalExceptionHandler} 认得的<b>唯一形状</b>。</td></tr>
 *   <tr><td>{@code org.icedamericanomall.exception.UpstreamException}</td><td>{@code ia-api}</td>
 *       <td>本类在 <b>Feign 场景</b>的具体化（历史类名保留，30 个降级工厂与全部调用方零改动）。</td></tr>
 * </table>
 *
 * <p><b>业务代码不要直接 {@code new} 本类</b>：它是承载层，不是业务出口。业务失败请用
 * {@code ia-common} 的 {@code BizException} / {@code BadRequestException} 等（带本域
 * {@link org.icedamericanomall.common.enums.ErrorCode}）。本类的实例只应由
 * 「跨服务调用的解码/降级路径」产生 —— 目前唯一的生产者是 {@code ia-api} 的
 * {@code UpstreamErrorDecoder} 与各降级工厂。
 *
 * <h2>状态码口径（{@link #httpStatus()}）</h2>
 * <p>{@link ErrorInfo#category()} 是由 HTTP 状态码<b>派生</b>的（{@link ErrorCategory}），
 * 方向是「状态码 → 类别」；本类的 {@link #httpStatus()} 做的是<b>反向单点还原</b>，
 * 且只在「能还原」的类别上还原：
 * <ul>
 *   <li>{@link ErrorCategory#CLIENT}：<b>原样用下游状态码</b>（404 就是 404、409 就是 409）——
 *       这是「透传」的定义本身；下游给了 4xx 区间之外的值（如连接层失败时传 0）才回落 400。</li>
 *   <li>{@link ErrorCategory#RULE}：下游用 200 + 业务码承载「业务规则拒绝」，还原为 <b>200</b>。</li>
 *   <li>{@link ErrorCategory#TRANSIENT}：<b>503</b>（下游不可用 / 超时 / 熔断）。</li>
 *   <li>{@link ErrorCategory#BUG}：<b>500</b>（下游代码缺陷，细节不外泄）。</li>
 * </ul>
 * <p>还原规则<b>只写在这里一处</b>：{@code GlobalExceptionHandler} 与测试都调它。任何在
 * 别处再写一份 {@code switch (category)} 的代码，都会在新增类别时与本处漂移。
 */
public class CarriedErrorException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** 异常链回溯上限 —— 防止畸形/自引用链把降级路径拖成死循环。 */
    private static final int MAX_CAUSE_DEPTH = 16;

    /** 4xx 区间（含端点）—— CLIENT 类别原样透传的合法范围。 */
    private static final int CLIENT_STATUS_MIN = 400;
    private static final int CLIENT_STATUS_MAX = 499;

    /** 下游错误体（业务码 / 文案 / 类别 / 可重试性 / traceId）。 */
    private final ErrorInfo errorInfo;

    /** 下游真实 HTTP 状态码（0 表示未知，例如连接层失败）。 */
    private final int upstreamStatus;

    /**
     * @param errorInfo      下游错误体（不可为 {@code null}）
     * @param upstreamStatus 下游真实 HTTP 状态码（未知时传 0）
     */
    protected CarriedErrorException(ErrorInfo errorInfo, int upstreamStatus) {
        this(errorInfo, upstreamStatus, null);
    }

    /**
     * @param errorInfo      下游错误体（不可为 {@code null}）
     * @param upstreamStatus 下游真实 HTTP 状态码（未知时传 0）
     * @param cause          原始异常（可为 {@code null}）
     */
    protected CarriedErrorException(ErrorInfo errorInfo, int upstreamStatus, Throwable cause) {
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
     * 应答本次请求应使用的 HTTP 状态码（<b>全仓唯一还原入口</b>，规则见类注释）。
     *
     * @return 永不为 {@code null}
     */
    public final HttpStatus httpStatus() {
        return switch (getCategory()) {
            case CLIENT -> clientStatus();
            case RULE -> HttpStatus.OK;
            case TRANSIENT -> HttpStatus.SERVICE_UNAVAILABLE;
            case BUG -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }

    /**
     * CLIENT 类别：原样用下游 4xx；下游状态码不可用（0 / 超范围 / 非标准值）才回落 400。
     *
     * <p>为什么回落 400 而不是 500：类别既然是 CLIENT（调用方的错），把「本该算调用方的账」
     * 记到服务端头上只会让监控与重试策略同时误判（{@code 09-constraints} §三·八·3）。
     */
    private HttpStatus clientStatus() {
        if (upstreamStatus < CLIENT_STATUS_MIN || upstreamStatus > CLIENT_STATUS_MAX) {
            return HttpStatus.BAD_REQUEST;
        }
        HttpStatus resolved = HttpStatus.resolve(upstreamStatus);
        return resolved == null ? HttpStatus.BAD_REQUEST : resolved;
    }

    /**
     * 在异常链中查找指定类型的载体异常 —— 熔断/降级/超时装饰器都可能再包一层，
     * 直接 {@code instanceof} 判断第一层会漏掉真正的上游语义。
     *
     * @param cause 任意异常（可为 {@code null}）
     * @param type  期望的载体类型
     * @param <T>   载体类型
     * @return 找到的载体异常；不存在则 {@code null}
     */
    public static <T extends CarriedErrorException> T findCarriedIn(Throwable cause, Class<T> type) {
        Objects.requireNonNull(type, "type");
        Throwable current = cause;
        for (int depth = 0; current != null && depth < MAX_CAUSE_DEPTH; depth++) {
            if (type.isInstance(current)) {
                return type.cast(current);
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
