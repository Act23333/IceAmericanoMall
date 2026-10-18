package org.icedamericanomall.exception;

import org.icedamericanomall.common.api.ErrorInfo;
import org.icedamericanomall.common.exception.CarriedErrorException;

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
 * <p><b>继承关系（2026-10-18 修正，此前是「4xx 透传」断链的根因）</b>：
 * 本类现在继承 {@link CarriedErrorException}（{@code ia-common}），而<b>不再直接继承
 * {@code RuntimeException}</b>。
 * <ul>
 *   <li><b>为什么不继承 {@code CommonException}</b>（原判据不变）：{@code CommonException} 强制
 *       绑定一个本域的 {@code ErrorCode} —— 而下游的业务码（如 1103）在本域未必有同名枚举项，
 *       硬套会逼出「用自己的码替代下游码」的改写，正是 §三·八 要禁止的。故本异常自带
 *       {@link ErrorInfo}，不依赖本域枚举。</li>
 *   <li><b>为什么原来的「直接继承 {@code RuntimeException}」是缺陷</b>：那样它只能落到
 *       {@code GlobalExceptionHandler#handleException(Exception)} 的兜底分支 →
 *       {@code INTERNAL_ERROR} / <b>HTTP 500</b>。也就是说降级工厂<b>正确地</b>
 *       {@code throw upstream} 之后，响应仍然是一个 500 —— §三·八·1 的透传在
 *       <b>最后一米</b>被碾平。根因是模块方向：{@code ia-common} 不能反向依赖
 *       {@code ia-api}（否则成环），所以「透传所需的形状」必须下沉到 {@code ia-common}，
 *       即 {@link CarriedErrorException}；本类只是它在 Feign 场景的具体化。</li>
 *   <li>对调用方的可见行为<b>零变化</b>：全部 getter 与 {@link #findIn(Throwable)} 的签名、
 *       语义保持不变（{@code ia-api} 内 30 个降级工厂与数十处调用点一个字都不用改）。</li>
 * </ul>
 *
 * <p><b>降级语义（重要）</b>：调用方的 fallback 工厂<u>不得</u>无条件把它换成
 * {@code SYSTEM_BUSY}。正确做法见 {@code SpuDetailClientFallback}：
 * {@code category == CLIENT} → <b>原样抛出本异常</b>；其余（{@code TRANSIENT} / {@code BUG}）
 * 才按本域口径降级。这条语义由两只门禁机械强制（{@code FallbackCategoryGuardTest} 结构层、
 * {@code FallbackDegradeContractTest} 语义层）。
 */
public class UpstreamException extends CarriedErrorException {

    private static final long serialVersionUID = 1L;

    /**
     * @param errorInfo      下游错误体（不可为 {@code null}）
     * @param upstreamStatus 下游真实 HTTP 状态码（未知时传 0）
     */
    public UpstreamException(ErrorInfo errorInfo, int upstreamStatus) {
        super(errorInfo, upstreamStatus);
    }

    /**
     * @param errorInfo      下游错误体（不可为 {@code null}）
     * @param upstreamStatus 下游真实 HTTP 状态码（未知时传 0）
     * @param cause          原始异常（可为 {@code null}）
     */
    public UpstreamException(ErrorInfo errorInfo, int upstreamStatus, Throwable cause) {
        super(errorInfo, upstreamStatus, cause);
    }

    /**
     * 在异常链中查找本类异常 —— 熔断/降级/超时装饰器都可能再包一层，直接 {@code instanceof}
     * 判断第一层会漏掉真正的上游语义。
     *
     * <p>与父类 {@link CarriedErrorException#findCarriedIn(Throwable, Class)} 共用同一条
     * 回溯循环（含深度上限与自引用保护），本方法只做类型收窄 —— 语义与历史上本类自持的
     * 那份实现逐字一致。
     *
     * @param cause 任意异常（可为 {@code null}）
     * @return 找到的 {@link UpstreamException}；不存在则 {@code null}
     */
    public static UpstreamException findIn(Throwable cause) {
        return CarriedErrorException.findCarriedIn(cause, UpstreamException.class);
    }
}
