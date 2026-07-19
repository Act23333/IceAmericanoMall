package org.noLazy.common.enums;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {
    // 系统级错误 (HTTP 状态码统一用 500)
    INTERNAL_ERROR(1000, "服务器内部错误", HttpStatus.INTERNAL_SERVER_ERROR),
    
    // 认证/授权类错误 (HTTP 状态码用 401/403)
    UNAUTHORIZED(1001, "未登录", HttpStatus.UNAUTHORIZED),
    TOKEN_EXPIRED(1002, "登录已过期", HttpStatus.UNAUTHORIZED),
    FORBIDDEN(1003, "无权限操作", HttpStatus.FORBIDDEN),
    FREQUENT_ERROR(1004, "操作过于频繁", HttpStatus.FORBIDDEN),
    ILLEGAL_REQUEST(1005, "非法请求", HttpStatus.BAD_REQUEST),

    // 参数/资源类错误 (HTTP 状态码用 400/404)
    PARAM_ERROR(1101, "参数错误", HttpStatus.BAD_REQUEST),
    USER_NOT_FOUND(1102, "用户不存在", HttpStatus.NOT_FOUND),
    
    // 业务逻辑错误 (HTTP 状态码统一用 200)
    BUSINESS_EXECUTION_EXCEPTION(3000, "业务执行异常", HttpStatus.OK),
    CAPTCHA_ERROR(3001, "验证码错误", HttpStatus.OK),
    CAPTCHA_EXPIRED(3002, "验证码已过期", HttpStatus.OK),
    PHONE_ALREADY_REGISTERED(3003, "手机号已被注册", HttpStatus.OK),
    USERNAME_ALREADY_TAKEN(3004, "用户名已被占用", HttpStatus.OK),
    BALANCE_INSUFFICIENT(3005, "余额不足", HttpStatus.OK),
    USER_ALREADY_EXISTS(3006, "用户已存在", HttpStatus.OK),
    PASSWORD_ERROR(3007, "登录密码错误", HttpStatus.OK),
    USER_STATUS_ABNORMAL(3008, "用户状态异常", HttpStatus.OK),

    // AI 服务错误 (3100-3199)
    AI_SERVICE_UNAVAILABLE(3100, "AI 服务未启用或 API Key 未配置", HttpStatus.SERVICE_UNAVAILABLE),
    AI_TIMEOUT(3101, "AI 调用超时，请稍后重试", HttpStatus.GATEWAY_TIMEOUT),
    AI_CONTENT_FILTERED(3102, "内容未通过安全审核", HttpStatus.BAD_REQUEST),
    AI_RATE_LIMITED(3103, "AI 助手正在忙碌中，请稍后再试", HttpStatus.TOO_MANY_REQUESTS),
    AI_CONVERSATION_NOT_FOUND(3104, "会话不存在或无权访问", HttpStatus.NOT_FOUND),
    ;


    private final int code;          // 业务码
    private final String message;    // 默认提示信息
    private final HttpStatus httpStatus; // HTTP 状态码

    ErrorCode(int code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }
}