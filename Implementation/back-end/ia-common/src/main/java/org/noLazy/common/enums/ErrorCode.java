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
    // 交易/订单/商品通用错误 (3009-3029)
    ORDER_NOT_FOUND(3009, "订单不存在", HttpStatus.OK),
    ORDER_STATUS_INVALID(3010, "当前订单状态不支持此操作", HttpStatus.OK),
    PRODUCT_NOT_FOUND(3011, "商品不存在", HttpStatus.OK),
    STOCK_INSUFFICIENT(3012, "库存不足", HttpStatus.OK),
    CART_EMPTY(3013, "购物车为空", HttpStatus.OK),
    CROSS_STORE_FORBIDDEN(3014, "暂不支持跨店下单", HttpStatus.OK),
    // 秒杀错误 (3020-3029)
    FLASH_SALE_NOT_FOUND(3020, "秒杀活动不存在", HttpStatus.OK),
    FLASH_SALE_NOT_STARTED(3021, "秒杀未开始或已结束", HttpStatus.OK),
    FLASH_SALE_SOLD_OUT(3022, "已售罄", HttpStatus.OK),
    FLASH_SALE_LIMIT_EXCEEDED(3023, "每人限购1件", HttpStatus.OK),
    FLASH_SALE_FAILED(3024, "抢购失败，请重试", HttpStatus.OK),
    // 售后/申请 (3030-3039)
    APPLICATION_EXISTS(3030, "已有审核中的申请", HttpStatus.OK),
    AFTER_SALE_EXISTS(3031, "已有售后申请", HttpStatus.OK),
    APPLICATION_NOT_FOUND(3032, "申请不存在", HttpStatus.OK),
    WITHDRAWAL_NOT_FOUND(3033, "提现申请不存在", HttpStatus.OK),

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