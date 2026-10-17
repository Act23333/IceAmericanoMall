package org.icedamericanomall.exception;

import org.icedamericanomall.common.enums.ErrorCategory;

/**
 * <b>跨服务失败的唯一类别判定入口</b>（{@code ia-api} 侧收口）—— 解码路径与降级路径共用。
 *
 * <p><b>为什么还需要它</b>：{@link ErrorCategory#of(Integer, Throwable)} 是全仓唯一的
 * 「异常类型 → 状态码 → 500 才是 BUG」判据，但它<b>看不到「已类型化的上游错误体」</b>
 * —— {@link UpstreamException} 属于 {@code ia-api}，而 {@code ia-common} 不能反向依赖
 * {@code ia-api}。若两条路径各自拼一次判定，就会出现「解码器说 TRANSIENT、降级工厂说是 BUG」
 * 这种同一枚故障两套结论（{@code 09-constraints} §三·八 立条要消灭的正是这种分裂）。
 * 故把「装饰/承载层的类别」并入同一个入口，由解码器
 * （{@link UpstreamErrorDecoder}）与降级工厂（如 {@code SpuDetailClientFallback}）共同调用。
 *
 * <p><b>判定顺序（不可调换）</b>：
 * <ol>
 *   <li><b>异常类型</b>：{@link ErrorCategory#isTransientCause(Throwable)} 命中 → {@code TRANSIENT}
 *       （连接失败 / 超时 / 熔断打开 / 池耗尽 —— 这些<b>没有 HTTP 响应</b>，是最容易被
 *       「只看状态码」漏掉的一类）；</li>
 *   <li><b>已类型化的上游错误体</b>（{@link UpstreamException#getCategory()}）：下游/解码器
 *       已给出显式结论，原样采信 —— 包括下游 500 但自报 {@code TRANSIENT} 的情况；</li>
 *   <li><b>HTTP 状态码</b>：{@link ErrorCategory#of(Integer, Throwable)} 派生
 *       （4xx → CLIENT、2xx → RULE、408/429/5xx-infra → TRANSIENT、<b>500 → BUG</b>）；</li>
 *   <li>既无异常类型线索也无状态码（如 {@code cause == null}）→ {@code TRANSIENT}：
 *       未知按可重试处理，<b>绝不当成功</b>。</li>
 * </ol>
 *
 * <p><b>为什么「异常类型」排在「已类型化错误体」之前</b>：熔断打开 / 连接失败会把原始异常
 * <b>包在</b>外面（装饰器常态），此时「这次调用根本没打出去」是更强的事实，不能被内层
 * 的业务类别覆盖；反之若无任何类型线索，才轮到承载层与状态码表态。
 */
public final class UpstreamFailures {

    private UpstreamFailures() {
    }

    /**
     * 判定降级原因的处置类别（降级工厂入口：手上只有 {@code Throwable}，没有响应）。
     *
     * @param cause 触发降级的异常（可为 {@code null}）
     * @return 处置类别，永不为 {@code null}
     */
    public static ErrorCategory classify(Throwable cause) {
        return classify(null, cause);
    }

    /**
     * 判定降级原因的处置类别（统一入口：解码路径与降级路径共用）。
     *
     * @param httpStatus 下游真实 HTTP 状态码；无响应（连接失败/熔断/超时）时传 {@code null}
     * @param cause      触发失败的异常（可为 {@code null}）
     * @return 处置类别，永不为 {@code null}
     */
    public static ErrorCategory classify(Integer httpStatus, Throwable cause) {
        // ① 异常类型优先：连接 / 超时 / 熔断打开 / 池耗尽（没有 HTTP 响应可看）
        if (ErrorCategory.isTransientCause(cause)) {
            return ErrorCategory.TRANSIENT;
        }
        // ② 已类型化的上游错误体：下游/解码器给出的显式结论，原样采信
        UpstreamException upstream = UpstreamException.findIn(cause);
        if (upstream != null) {
            return upstream.getCategory();
        }
        // ③ 状态码派生 → 500 且无异常线索才是 BUG；④ 都无线索 → 未知即 TRANSIENT
        return ErrorCategory.of(httpStatus, null);
    }
}
