package org.icedamericanomall.common.aspect;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.common.api.ErrorInfo;
import org.icedamericanomall.common.api.Result;
import org.icedamericanomall.common.enums.ErrorCategory;
import org.icedamericanomall.common.exception.CarriedErrorException;
import org.icedamericanomall.common.exception.CommonException;
import org.icedamericanomall.common.filter.TraceIdFilter;
import org.icedamericanomall.common.utils.WebUtils;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * 异常应答的<b>共用构件</b> —— 抽出来是为了让 {@link GlobalExceptionHandler} 只留
 * 「异常类型 → 出口」的映射表，而把<b>响应形态</b>（内部 {@code ErrorInfo} / 对外 {@code Result}）
 * 与<b>上游透传</b>（{@link CarriedErrorException}）的实现集中在一处、只有一份。
 *
 * <p><b>为什么要有这个类（不是随手重构）</b>：{@code GlobalExceptionHandler} 在类长度棘轮基线
 * （{@code class-size-baseline.tsv}）里登记为「不许增长」，且它的到期拆类工作属 D 批。
 * 若把「上游 4xx 透传」整段塞进去，直接违反硬约束「类 ≤ 300 行」；更糟的是会把
 * 「错误码映射表」与「跨服务透传」两种职责继续搅在一个类里 —— 而后者恰恰是一个
 * <b>独立的失败维度</b>（下游的错误体 vs 本域的错误码）。
 *
 * <p><b>两个响应形态（跨服务口径的唯一实现）</b>：
 * <ul>
 *   <li><b>内部 {@code /internal/**}</b>：裸状态码 + {@link ErrorInfo}（不包 {@code Result}）——
 *       内部接口由 Feign 调用，调用方需要的是「真实状态码 + 足以判定处置的字段」；</li>
 *   <li><b>对外 {@code /api/**}</b>：{@link Result} 信封，键集合恒为 {@code code/msg/data}
 *       （{@code 09-constraints} §三·七·1，前端契约零变化）。</li>
 * </ul>
 */
@Slf4j
final class ExceptionResponseSupport {

    /** 内部接口路径标识 —— 命中即按「裸状态码 + {@link ErrorInfo}」应答。 */
    static final String INTERNAL_PATH_SEGMENT = "/internal/";

    private ExceptionResponseSupport() {
    }

    /**
     * 判断当前请求是否为内部接口（{@code /internal/} 路径段）。
     *
     * <p>无请求上下文（如定时任务/异步线程内抛异常）时按外部接口处理，避免 NPE。
     */
    static boolean isInternalRequest() {
        String uri = currentUri();
        return uri != null && uri.contains(INTERNAL_PATH_SEGMENT);
    }

    /**
     * 当前请求 URI；无请求上下文时返回 {@code null}。
     *
     * <p>日志字段缺失<b>绝不</b>升级成异常 —— 否则一次「取不到 URI」会把业务失败变成 500。
     */
    static String currentUri() {
        HttpServletRequest request = WebUtils.getRequest();
        return request == null ? null : request.getRequestURI();
    }

    /**
     * 读取当前请求的 traceId —— 复用网关 {@code X-Trace-Id} → {@code TraceIdFilter} → MDC 这条
     * <b>现成链路</b>（不引入 micrometer-tracing / skywalking：那是跨切面改造，与本口径无关）。
     *
     * <p>取不到（异步线程、无 MDC 上下文、过滤器未装配）一律 {@code null} ——
     * <b>绝不</b>抛异常、<b>绝不</b>因为「追踪号缺失」把一次业务失败升级成 500。
     */
    static String currentTraceId() {
        try {
            return MDC.get(TraceIdFilter.TRACE_ID);
        } catch (RuntimeException ex) {
            // MDC 实现异常（如上下文未装配）不得影响错误响应本身
            return null;
        }
    }

    /**
     * 本域异常的<b>内部</b>响应：状态码取自 {@code ErrorCode}，body 为 {@link ErrorInfo}。
     *
     * <p>不含 {@code data}（payload），避免把可能带 PII 的内部数据回传给调用方日志。
     * {@code category} / {@code retryable} 一律<b>派生</b>（{@link ErrorInfo#of}），不手填 ——
     * 手填必然在某处与状态码矛盾，而调用方会照着矛盾的那一半做决策。
     */
    static ResponseEntity<ErrorInfo> rawInternalResponse(CommonException e) {
        ErrorInfo body = ErrorInfo.of(e.getErrorCode(), e.getDisplayMessage(), currentTraceId());
        return ResponseEntity.status(e.getErrorCode().getHttpStatus()).body(body);
    }

    /**
     * 已类型化上游错误的应答（{@link CarriedErrorException}）—— 跨服务 4xx 透传的落地形态。
     *
     * <p><b>两侧都用 {@link CarriedErrorException#httpStatus()} 这一个还原入口</b>取状态码；
     * 内部出口直接把下游那份 {@link ErrorInfo} 原样回传（业务码、文案、category、retryable、
     * traceId 一个字段都不重算），对外出口只换信封、不换码。
     *
     * <p>刻意<b>不做</b>的两件事：
     * <ol>
     *   <li><b>不把下游业务码映射成本域错误码</b>：那正是 §三·八·1 禁止的改写
     *       （下游的 1103「资源不存在」到了本域仍应是 1103）；</li>
     *   <li><b>不重算 {@code category} / {@code retryable}</b>：内部出口用下游那份
     *       {@link ErrorInfo}，因为它就是「下游对本次调用的判定」；在本域重算会让多跳链
     *       每一跳都丢掉上一跳的处置结论。</li>
     * </ol>
     *
     * @param e 载体异常
     * @return 内部为 {@code ResponseEntity<ErrorInfo>}、对外为 {@code ResponseEntity<Result>}
     */
    static Object carriedResponse(CarriedErrorException e) {
        HttpStatus status = e.httpStatus();
        logCarried(e, status);
        if (isInternalRequest()) {
            return ResponseEntity.status(status).body(e.getErrorInfo());
        }
        return ResponseEntity.status(status).body(Result.error(e.getCode(), e.getMsg()));
    }

    /**
     * 上游错误的日志出口。
     *
     * <p><b>为什么级别不是一律 ERROR</b>：{@code CLIENT} / {@code RULE} 是<b>调用方的错</b>，
     * 把它们记成 ERROR 会让告警与「本服务代码缺陷」混在一起（客户传错参数不该惊动值班）；
     * 只有 {@code TRANSIENT}（下游不可用）与 {@code BUG}（下游缺陷）才升为 ERROR 并带堆栈。
     */
    private static void logCarried(CarriedErrorException e, HttpStatus status) {
        String url = currentUri();
        if (e.getCategory() == ErrorCategory.CLIENT || e.getCategory() == ErrorCategory.RULE) {
            // 调用方的错：不刷 ERROR 堆栈，否则真实故障会被客户错误淹掉
            log.warn("上游业务失败透传: url={}, httpStatus={}, upstreamStatus={}, code={}, category={}, msg={}",
                    url, status.value(), e.getUpstreamStatus(), e.getCode(), e.getCategory(), e.getMsg());
            return;
        }
        log.error("上游不可用或缺陷: url={}, httpStatus={}, upstreamStatus={}, code={}, category={}, retryable={}, msg={}",
                url, status.value(), e.getUpstreamStatus(), e.getCode(), e.getCategory(), e.isRetryable(),
                e.getMsg(), e);
    }
}
