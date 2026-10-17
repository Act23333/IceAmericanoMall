package org.icedamericanomall.common.aspect;

import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.api.ErrorInfo;
import org.icedamericanomall.common.api.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.icedamericanomall.common.exception.*;
import org.icedamericanomall.common.filter.TraceIdFilter;
import org.icedamericanomall.common.utils.WebUtils;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.validation.BindException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import java.util.Objects;
import java.util.stream.Collectors;
import static org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type.SERVLET;

/**
 * 全局异常处理器 —— 把 {@code ia-common} 异常体系统一映射为
 * {@code ResponseEntity<Result<Object>>}（HTTP 状态码取自 {@code ErrorCode}）。
 *
 * <p>两个关键约定：
 * <ul>
 *   <li>{@code /internal/**} 路径的业务异常<b>不包装 {@code Result}</b>，而是直接用
 *       {@code ErrorCode} 的 HTTP 状态码应答（body 为 {@link ErrorInfo}：
 *       {@code code / msg / category / retryable / traceId}）—— 见
 *       {@link #rawInternalResponse(CommonException)}。内部接口由 Feign 调用，调用方需要的是
 *       「真实 HTTP 状态码」+「足以判定处置的字段」，而不是 {@code Result} 信封。
 *       <p><b>V5.3 修正（P1 缺陷）</b>：此前实现是在 {@code @ExceptionHandler} 里
 *       {@code throw e;} 想「原样抛出」，但 {@code HandlerExceptionResolver} 捕获异常后
 *       <b>不会二次解析</b>新抛出的异常，最终由容器兜底成 HTTP 500 —— 一切内部
 *       {@code BizException}（含 429/400 等确定性失败）都伪装成「服务宕机」，
 *       Feign 调用方无法区分业务失败与故障。现改为直接返回
 *       {@code ResponseEntity.status(errorCode.getHttpStatus())}，语义与 ADR-004 的本意一致。</li>
 *   <li>本类<b>不再依赖</b> {@code RateLimitUtils}（V5.2）：此前持有一个从未被使用的
 *       {@code private final RateLimitUtils rateLimitUtils} 字段，构成一条隐性的
 *       Redis 硬依赖 —— 无 Redis 的服务即使不看异常处理逻辑也会因缺少该 Bean 而启动失败。
 *       删除该死字段后，全局异常处理在任意类路径下都可独立存活。</li>
 * </ul>
 *
 * <p><b>2026-10-18 批次 ⑤（错误体类型化）</b>：内部错误体由原来的极简
 * {@code {"code","msg"}} 扩为 {@link ErrorInfo}（新增 {@code category / retryable / traceId}）。
 * 这是<b>向后兼容</b>的字段追加：生产环境 Jackson 的
 * {@code FAIL_ON_UNKNOWN_PROPERTIES=false}（Spring Boot 默认），存量按 {@code code}/{@code msg}
 * 解析的调用方不受影响。依据 {@code collaboration/09-constraints.md} §三·八·3。
 * <b>对外 {@code /api/**} 的响应一个字节都没变</b> —— 仍是 {@link Result}，
 * 见 {@link #processResponse(CommonException)}。
 *
 * @ClassName: CommonExceptionAdvice
 * @Description: TODO
 * @Author: noLazy
 * @Date: 2026/3/17 15:20
 * @Version: 1.0.0
 * @ProjectName: IcedAmericanoMall
 * @Package: org.icedamericanomall.common.aspect
 */
@Slf4j
@RestControllerAdvice
@ConditionalOnWebApplication(type = SERVLET)
public class GlobalExceptionHandler {

    /** 内部接口路径标识 —— 命中即按「裸状态码 + {@link ErrorInfo}」应答（见类注释）。 */
    private static final String INTERNAL_PATH_SEGMENT = "/internal/";

    /**
     * 处理自定义异常
     */
    @ExceptionHandler(CommonException.class)
    public Object handleCommonException(CommonException e) {
        printLog(e, "自定义异常: url={}, httpStatus={}, bizCode={}, msg={}");
        return processResponse(e);
    }

    private static void printLog(CommonException e, String format) {
        ErrorCode errorCode = e.getErrorCode();
        int bizCode = errorCode.getCode();
        String message = e.getDisplayMessage();
        HttpStatus httpStatus = errorCode.getHttpStatus();
        String url = Objects.requireNonNull(WebUtils.getRequest()).getRequestURI();
        log.error(format, url, httpStatus, bizCode, message, e);
    }

    /**
     * 处理数据库异常
     */
    @ExceptionHandler(DBException.class)
    public Object handleDBException(DBException e) {
        printLog(e, "数据库异常: url={}, httpStatus={}, bizCode={}, msg={}");
        return processResponse(e);
    }

    /**
     * 处理业务异常（含内部接口裸状态码透传，见 {@link #processResponse(CommonException)}）。
     */
    @ExceptionHandler(BizException.class)
    public Object handleBizException(BizException e) {
        printLog(e, "业务异常: url={}, httpStatus={}, bizCode={}, msg={}");
        return processResponse(e);
    }

    /**
     * 处理禁止访问异常
     */
    @ExceptionHandler(ForbiddenException.class)
    public Object handleForbiddenException(ForbiddenException e) {
        printLog(e, "禁止访问异常: url={}, httpStatus={}, bizCode={}, msg={}");
        return processResponse(e);
    }

    /**
     * 处理未授权异常
     */
    @ExceptionHandler(UnauthorizedException.class)
    public Object handleUnauthorizedException(UnauthorizedException e) {
        printLog(e, "未授权异常: url={}, httpStatus={}, bizCode={}, msg={}");
        return processResponse(e);
    }

    /**
     * 处理参数校验异常（@Valid 抛出的异常）
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Object handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(ObjectError::getDefaultMessage)
                .collect(Collectors.joining("|"));
        BadRequestException badRequestException = new BadRequestException(ErrorCode.PARAM_ERROR, msg, e);
        printLog(badRequestException, "参数校验异常: url={}, httpStatus={}, bizCode={}, msg={}");
        return processResponse(badRequestException);
    }

    /**
     * 处理参数绑定异常（如普通表单提交校验）
     */
    @ExceptionHandler(BindException.class)
    public Object handleBindException(BindException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("；"));
        BadRequestException badRequestException = new BadRequestException(ErrorCode.PARAM_ERROR, msg, e);
        printLog(badRequestException, "参数绑定异常: url={}, httpStatus={}, bizCode={}, msg={}");
        return processResponse(badRequestException);
    }

    /**
     * 处理方法级参数校验异常（{@code @Validated} 标注的 Controller 上，
     * {@code @RequestParam} / {@code @PathVariable} 等<b>非 body</b> 参数违反约束时抛出）。
     *
     * <p><b>为何需要（V5.4 补齐实测缺口）</b>：{@code @Valid} 校验 {@code @RequestBody} 失败抛
     * {@link MethodArgumentNotValidException}（已处理），而类上标注 {@code @Validated} 的方法参数
     * 校验失败抛的是 {@link ConstraintViolationException}。此前未映射，会被兜底的
     * {@link #handleException(Exception)} 折叠成 {@code INTERNAL_ERROR}（HTTP 500），
     * 前端看到「服务异常」而非「参数错误」——marketing-service {@code /internal/points} 实测缺口。
     *
     * <p>映射为 {@code PARAM_ERROR}（HTTP 400），与 {@link MethodArgumentNotValidException} /
     * {@link BindException} 同一语义出口；违反明细按 {@code propertyPath: message} 拼接。
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public Object handleConstraintViolationException(ConstraintViolationException e) {
        String msg = e.getConstraintViolations() == null || e.getConstraintViolations().isEmpty()
                ? e.getMessage()
                : e.getConstraintViolations().stream()
                        .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                        .collect(Collectors.joining("；"));
        BadRequestException badRequestException = new BadRequestException(ErrorCode.PARAM_ERROR, msg, e);
        printLog(badRequestException, "方法参数校验异常: url={}, httpStatus={}, bizCode={}, msg={}");
        return processResponse(badRequestException);
    }

    /**
     * 处理「必填请求参数缺失」（{@code @RequestParam} 未传且非可选）。
     *
     * <p>此前无专属出口 → 落 {@link #handleException} 兜底为 500 {@code INTERNAL_ERROR}，
     * 与契约 §1.3「全平台方法参数校验失败统一映射为 400 {@code PARAM_ERROR(1101)}」冲突。
     * 实测缺口：{@code GET /internal/coupon/batch} 不带 {@code ids} 时返回 500（ADR-055 登记）。
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public Object handleMissingServletRequestParameterException(MissingServletRequestParameterException e) {
        BadRequestException badRequestException = new BadRequestException(
                ErrorCode.PARAM_ERROR, "缺少必填参数: " + e.getParameterName(), e);
        printLog(badRequestException, "请求参数缺失: url={}, httpStatus={}, bizCode={}, msg={}");
        return processResponse(badRequestException);
    }

    /**
     * 处理「参数类型不匹配」（如 {@code ?page=abc}、路径变量非数字）→ 400 {@code PARAM_ERROR}。
     *
     * <p>此前同样落兜底 500；类型错误属调用方输入错误，语义上是参数校验失败而非服务异常。
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public Object handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException e) {
        BadRequestException badRequestException = new BadRequestException(
                ErrorCode.PARAM_ERROR,
                "参数类型不匹配: " + e.getName() + "=" + e.getValue(), e);
        printLog(badRequestException, "请求参数类型异常: url={}, httpStatus={}, bizCode={}, msg={}");
        return processResponse(badRequestException);
    }

    /**
     * 处理「请求体不可读」（JSON 语法错误 / 字段类型不符 / body 为空）→ 400 {@code PARAM_ERROR}。
     *
     * <p>此前落兜底 500；报文格式错误属调用方输入错误。
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Object handleHttpMessageNotReadableException(HttpMessageNotReadableException e) {
        BadRequestException badRequestException = new BadRequestException(
                ErrorCode.PARAM_ERROR, "请求体格式错误或不可读", e);
        printLog(badRequestException, "请求体解析异常: url={}, httpStatus={}, bizCode={}, msg={}");
        return processResponse(badRequestException);
    }

    /**
     * 处理静态资源 404（如 favicon.ico）— 不打印堆栈，仅返回 404
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public Object handleNoResourceFoundException(NoResourceFoundException e) {
        HttpException httpException = new HttpException(ErrorCode.NO_RESOURCE_FOUND,e);
        printLog(httpException, "静态资源获取异常: url={}, httpStatus={}, bizCode={}, msg={}");
        return processResponse(httpException);
    }

    /**
     * 处理其他所有未捕获的异常（兜底）
     */
    @ExceptionHandler(Exception.class)
    public Object handleException(Exception e) {
        CommonException commonException = new CommonException(ErrorCode.INTERNAL_ERROR, e);
        return handleCommonException(commonException);
    }

    /**
     * 异常 → 响应：外部接口包装为 {@code Result}，内部接口（{@code /internal/**}）只回真实状态码。
     *
     * <p>内部接口之所以仍带一个 JSON body（而非空 body）：{@code ErrorCode} 中大量业务码
     * （3000-3039「业务逻辑错误」）的 HTTP 状态本身就是 200，若 body 为空，Feign 调用方会拿到
     * 一个「2xx 且无内容」的响应 —— 既无法反序列化到声明的返回类型，也彻底丢失失败原因。
     * 这里刻意<b>不使用</b> {@code Result} 类（不构成信封契约），body 为 {@link ErrorInfo}：
     * 业务码 + 提示 + 处置类别 + 可重试性 + traceId，让调用方既能按 HTTP 状态码判定失败，
     * 也能按 {@code category} 判定<b>怎么处置</b>（{@code 09-constraints} §三·八·3）。
     *
     * <p><b>外部接口这一支一个字都没改</b>：仍返回 {@code Result.error(code, msg, data)}，
     * 前端契约（键集合与取值）与改动前逐字一致。
     */
    private Object processResponse(CommonException e) {
        if (isInternalRequest()) {
            return rawInternalResponse(e);
        }
        return ResponseEntity.status(e.getErrorCode().getHttpStatus())
                .body(Result.error(e.getErrorCode().getCode(), e.getDisplayMessage(), e.getData()));
    }

    /**
     * 判断当前请求是否为内部接口（{@code /internal/} 路径段）。
     *
     * <p>无请求上下文（如定时任务/异步线程内抛异常）时按外部接口处理，避免 NPE。
     */
    private static boolean isInternalRequest() {
        HttpServletRequest request = WebUtils.getRequest();
        return request != null && request.getRequestURI() != null
                && request.getRequestURI().contains(INTERNAL_PATH_SEGMENT);
    }

    /**
     * 内部接口错误响应：HTTP 状态码取自 {@code ErrorCode}，body 为 {@link ErrorInfo}。
     *
     * <p>不含 {@code data}（payload），避免把可能带 PII 的内部数据回传给调用方日志。
     * {@code category} / {@code retryable} 一律<b>派生</b>（{@link ErrorInfo#of}），不手填 —— 手填
     * 必然在某处与状态码矛盾，而调用方会照着矛盾的那一半做决策。
     */
    private static ResponseEntity<ErrorInfo> rawInternalResponse(CommonException e) {
        ErrorInfo body = ErrorInfo.of(e.getErrorCode(), e.getDisplayMessage(), currentTraceId());
        return ResponseEntity.status(e.getErrorCode().getHttpStatus()).body(body);
    }

    /**
     * 读取当前请求的 traceId —— 复用网关 {@code X-Trace-Id} → {@code TraceIdFilter} → MDC 这条
     * <b>现成链路</b>（不引入 micrometer-tracing / skywalking：那是跨切面改造，与本口径无关）。
     *
     * <p>取不到（异步线程、无 MDC 上下文、过滤器未装配）一律 {@code null} ——
     * <b>绝不</b>抛异常、<b>绝不</b>因为「追踪号缺失」把一次业务失败升级成 500。
     */
    private static String currentTraceId() {
        try {
            return MDC.get(TraceIdFilter.TRACE_ID);
        } catch (RuntimeException ex) {
            // MDC 实现异常（如上下文未装配）不得影响错误响应本身
            return null;
        }
    }
}