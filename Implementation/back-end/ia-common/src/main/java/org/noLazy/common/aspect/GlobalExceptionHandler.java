package org.noLazy.common.aspect;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.noLazy.common.enums.ErrorCode;
import org.noLazy.common.domain.Result;
import org.noLazy.common.exception.*;
import org.noLazy.common.utils.RateLimitUtils;
import org.noLazy.common.utils.UserContext;
import org.noLazy.common.utils.WebUtils;
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

import java.util.Objects;
import java.util.stream.Collectors;

import static org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type.SERVLET;

/**
 * @ClassName: CommonExceptionAdvice
 * @Description: TODO
 * @Author: noLazy
 * @Date: 2026/3/17 15:20
 * @Version: 1.0.0
 * @ProjectName: IcedAmericanoMall
 * @Package: org.noLazy.common.aspect
 */
@Slf4j
@RestControllerAdvice
@ConditionalOnWebApplication(type = SERVLET)
@RequiredArgsConstructor
public class GlobalExceptionHandler {
    private final RateLimitUtils rateLimitUtils;

    /**
     * 处理自定义异常
     */
    @ExceptionHandler(CommonException.class)
    public Object handleCommonException(CommonException e) {
        printLog(e, "自定义异常: httpStatus={}, bizCode={}, msg={}");
        return processResponse(e);
    }

    private static void printLog(CommonException e, String format) {
        ErrorCode errorCode = e.getErrorCode();
        int bizCode = errorCode.getCode();
        String message = e.getDisplayMessage();
        HttpStatus httpStatus = errorCode.getHttpStatus();
        log.error(format, httpStatus, bizCode, message, e);
    }

    /**
     * 处理数据库异常
     */
    @ExceptionHandler(DBException.class)
    public Object handleDBException(DBException e) {
        printLog(e, "数据库异常: httpStatus={}, bizCode={}, msg={}");
        return processResponse(e);
    }

    /**
     * 处理业务异常
     */
    @ExceptionHandler(BizException.class)
    public Object handleBizException(BizException e) {
        // 关键：内部接口 /internal/** → 直接抛HTTP异常，不包装Result,因为异常还会被调用方处理，方便调用方解析和处理异常
        String uri = Objects.requireNonNull(WebUtils.getRequest()).getRequestURI();
        if (uri.contains("/internal/")) {
            throw e;
        }
        printLog(e, "业务异常: httpStatus={}, bizCode={}, msg={}");
        return processResponse(e);
    }

    /**
     * 处理禁止访问异常
     */
    @ExceptionHandler(ForbiddenException.class)
    public Object handleForbiddenException(ForbiddenException e) {
        printLog(e, "禁止访问异常: httpStatus={}, bizCode={}, msg={}");
        return processResponse(e);
    }

    /**
     * 处理未授权异常
     */
    @ExceptionHandler(UnauthorizedException.class)
    public Object handleUnauthorizedException(UnauthorizedException e) {
        printLog(e, "未授权异常: httpStatus={}, bizCode={}, msg={}");
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
        log.error("参数校验异常：{}", msg, e);
        return processResponse(new BadRequestException(ErrorCode.PARAM_ERROR, e));
    }

    /**
     * 处理参数绑定异常（如普通表单提交校验）
     */
    @ExceptionHandler(BindException.class)
    public Object handleBindException(BindException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("；"));
        log.error("参数绑定异常：{}", msg, e);
        return processResponse(new BadRequestException(ErrorCode.PARAM_ERROR, e));
    }

    /**
     * 处理静态资源 404（如 favicon.ico）— 不打印堆栈，仅返回 404
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public Object handleNoResourceFound(NoResourceFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }

    /**
     * 处理其他所有未捕获的异常（兜底）
     */
    @ExceptionHandler(Exception.class)
    public Object handleException(Exception e) {
        log.error("系统异常, url: {}", Objects.requireNonNull(WebUtils.getRequest()).getRequestURI(), e);
        return processResponse(new CommonException(ErrorCode.INTERNAL_ERROR, e));
    }

    /**
     * 将业务异常中的状态码映射为 HTTP 状态码
     * 如果你还想让代码更简洁，也可以只返回 R，但在网关层统一将非 200 的业务码转换为对应的 HTTP 状态码。不过直接在异常处理器中处理更直观。
     */
    private ResponseEntity<Result<Void>> processResponse(CommonException e) {
        return ResponseEntity.status(e.getErrorCode().getHttpStatus()).body(Result.error(e));
    }
}