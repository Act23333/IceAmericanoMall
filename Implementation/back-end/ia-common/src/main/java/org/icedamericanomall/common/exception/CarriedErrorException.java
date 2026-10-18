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
 * <h2>状态码口径（{@link #httpStatus()}）—— <b>状态码优先，类别兜底</b></h2>
 * <p>{@link ErrorInfo#category()} 是<b>派生标签</b>（方向：状态码 → 类别），而且解码器会
 * <b>采信下游报文里写的 {@code category}</b>。所以两者可能打架。本类一律让<b>事实</b>优先：
 * <ul>
 *   <li><b>上游给过真实状态码（{@code 100..599}）→ 原样用它</b>，不做任何改写。
 *       这是「透传」的定义本身：下游在线上发了什么，我们就回什么。</li>
 *   <li><b>拿不到状态码</b>（连接层失败 / 超时 / 熔断，{@code upstreamStatus = 0}）才按类别还原：
 *       {@link ErrorCategory#CLIENT} → 400、{@link ErrorCategory#RULE} → 200、
 *       {@link ErrorCategory#TRANSIENT} → 503、{@link ErrorCategory#BUG} → 500。</li>
 * </ul>
 * <p><b>反之（类别优先）是有缺陷的写法</b>，实测两个症状：
 * {@code category=CLIENT}+状态码 500 会被回成 <b>400</b>（把服务端的账记到调用方头上，
 * {@code 09-constraints} §三·六 明文禁止）；{@code category=RULE}+非 2xx 会被回成 <b>200</b>
 * （用 200 承载失败，熔断器永不触发 —— §三·八·5）。详见 {@link #httpStatus()} 的 javadoc。
 *
 * <p>还原规则<b>只写在这里一处</b>：{@code GlobalExceptionHandler} 与测试都调它。任何在
 * 别处再写一份 {@code switch (category)} 的代码，都会在新增类别时与本处漂移。
 */
public class CarriedErrorException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** 异常链回溯上限 —— 防止畸形/自引用链把降级路径拖成死循环。 */
    private static final int MAX_CAUSE_DEPTH = 16;

    /** 合法 HTTP 状态码区间（含端点）—— 之外的一律视为「没有状态码线索」。 */
    private static final int MIN_HTTP_STATUS = 100;
    private static final int MAX_HTTP_STATUS = 599;

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
     * 应答本次请求应使用的 HTTP 状态码（<b>全仓唯一还原入口</b>）。
     *
     * <p><b>规则：状态码优先，类别只在拿不到状态码时兜底。</b>
     * <ol>
     *   <li>上游给过真实状态码（{@code 100..599}）→ <b>原样用它</b>，不做任何改写；</li>
     *   <li>拿不到（连接层失败 / 超时 / 熔断，{@code upstreamStatus = 0}；或超出合法区间）
     *       → 才按 {@link #getCategory()} 还原：{@code CLIENT} 400 / {@code RULE} 200 /
     *       {@code TRANSIENT} 503 / {@code BUG} 500。</li>
     * </ol>
     *
     * <p><b>为什么必须「状态码优先」而不是「类别优先」（此前写反了，是缺陷）</b>：
     * {@link #getCategory()} 是<b>派生标签</b>，且解码器会<b>采信下游报文里的 {@code category}</b>
     * （{@code UpstreamErrorDecoder#toErrorInfo}：「已类型化的下游优先，缺省才按状态码派生」）。
     * 于是下游报文自相矛盾时，两者会打架：<ul>
     *   <li>{@code category=CLIENT} + 状态码 500 → 若信类别就返回 <b>400</b>，
     *       即<b>把服务端的账记到调用方头上</b>，调用方会去改一个本来没错的请求
     *       （{@code 09-constraints} §三·六 明文禁止「谎报成 400」）；</li>
     *   <li>{@code category=RULE} + 非 2xx 状态码 → 若信类别就返回 <b>200</b>，
     *       即<b>用 200 承载失败</b>：熔断器永不触发、成功率指标虚高
     *       （{@code 09-constraints} §三·八·5「状态码是被观测面」）。</li>
     * </ul>
     * 而 HTTP 状态码是<b>传输层的观测事实</b>（调用方、网关、熔断器、指标都只看它），
     * 类别只是我们对它的解释。两者矛盾时，<b>事实优先于解释</b>。
     * 这条规则同时消灭了上述两类症状，且与「透传」的定义一致：下游在线上发了什么，我们就回什么。
     *
     * @return HTTP 状态码数值（永不为 {@code null}；用数值而非 {@code HttpStatus} 枚举，
     *         是为了让非标准的合法状态码（如 499）也能原样透传，不被枚举解析掉）
     */
    public final int httpStatus() {
        return hasObservedStatus() ? upstreamStatus : derivedStatus();
    }

    /** 上游是否给过可用的 HTTP 状态码（{@code 100..599} 之外一律视为「没有线索」）。 */
    private boolean hasObservedStatus() {
        return upstreamStatus >= MIN_HTTP_STATUS && upstreamStatus <= MAX_HTTP_STATUS;
    }

    /**
     * 没有状态码线索时按类别还原（只用于连接层失败 / 超时 / 熔断这类<b>根本没有响应</b>的故障）。
     *
     * <p>各分支的理由：{@code CLIENT} → 400（调用方的错）；{@code RULE} → 200（业务规则拒绝的既有
     * 形态就是 200 + 业务码）；{@code TRANSIENT} → 503（暂时不可用）；{@code BUG} → 500（缺陷）。
     */
    private int derivedStatus() {
        return switch (getCategory()) {
            case CLIENT -> HttpStatus.BAD_REQUEST.value();
            case RULE -> HttpStatus.OK.value();
            case TRANSIENT -> HttpStatus.SERVICE_UNAVAILABLE.value();
            case BUG -> HttpStatus.INTERNAL_SERVER_ERROR.value();
        };
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
