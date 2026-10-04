package org.icedamericanomall.common.aspect;

import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.api.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.icedamericanomall.common.exception.*;
import org.icedamericanomall.common.utils.WebUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.validation.BindException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import java.util.LinkedHashMap;
import java.util.Map;
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
 *       {@code ErrorCode} 的 HTTP 状态码应答（body 为极简 {@code {"code","msg"}} JSON）—— 见
 *       {@link #processResponse(CommonException)}。内部接口由 Feign 调用，调用方需要的是
 *       「真实 HTTP 状态码」而不是 {@code Result} 信封。
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

    /** 内部接口路径标识 —— 命中即按「裸状态码」应答（见类注释）。 */
    private static final String INTERNAL_PATH_SEGMENT = "/internal/";

    /** 内部接口极简错误体字段：业务码。 */
    private static final String FIELD_CODE = "code";

    /** 内部接口极简错误体字段：提示信息。 */
    private static final String FIELD_MSG = "msg";

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
     * <p>内部接口之所以仍带一个极简 JSON body（而非空 body）：{@code ErrorCode} 中大量业务码
     * （3000-3039「业务逻辑错误」）的 HTTP 状态本身就是 200，若 body 为空，Feign 调用方会拿到
     * 一个「2xx 且无内容」的响应 —— 既无法反序列化到声明的返回类型，也彻底丢失失败原因。
     * 这里刻意<b>不使用</b> {@code Result} 类（不构成信封契约），只暴露两个最小字段，
     * 让调用方既能按 HTTP 状态码判定失败，也能读到业务码与提示。
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
     * 内部接口错误响应：HTTP 状态码取自 {@code ErrorCode}，body 为极简 {@code {"code","msg"}}。
     *
     * <p>不含 {@code data}（payload），避免把可能带 PII 的内部数据回传给调用方日志。
     */
    private static ResponseEntity<Map<String, Object>> rawInternalResponse(CommonException e) {
        ErrorCode errorCode = e.getErrorCode();
        Map<String, Object> body = new LinkedHashMap<>(2);
        body.put(FIELD_CODE, errorCode.getCode());
        body.put(FIELD_MSG, e.getDisplayMessage());
        return ResponseEntity.status(errorCode.getHttpStatus()).body(body);
    }
}