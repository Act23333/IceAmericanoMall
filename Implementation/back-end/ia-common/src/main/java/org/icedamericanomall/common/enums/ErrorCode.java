package org.icedamericanomall.common.enums;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {
    // 系统级错误 (HTTP 状态码统一用 500)
    INTERNAL_ERROR(1000, "服务器内部错误", HttpStatus.INTERNAL_SERVER_ERROR),
    SYSTEM_BUSY(1001, "系统繁忙",  HttpStatus.SERVICE_UNAVAILABLE),

    // 认证/授权类错误 (HTTP 状态码用 401/403)
    UNAUTHORIZED(1011, "未登录", HttpStatus.UNAUTHORIZED),
    TOKEN_EXPIRED(1012, "登录已过期", HttpStatus.UNAUTHORIZED),
    FORBIDDEN(1013, "无权限操作", HttpStatus.FORBIDDEN),
    FREQUENT_ERROR(1014, "操作过于频繁", HttpStatus.FORBIDDEN),
    ILLEGAL_REQUEST(1015, "非法请求", HttpStatus.BAD_REQUEST),
    CAPTCHA_REQUIRED(1016, "人机验证失败", HttpStatus.FORBIDDEN),
    ACCOUNT_FROZEN(1017, "用户账号异常", HttpStatus.FORBIDDEN),

    // 参数/资源类错误 (HTTP 状态码用 400/404)
    PARAM_ERROR(1101, "参数错误", HttpStatus.BAD_REQUEST),
    USER_NOT_FOUND(1102, "用户不存在", HttpStatus.NOT_FOUND),
    NO_RESOURCE_FOUND(1103, "静态资源不存在", HttpStatus.NOT_FOUND),

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

    // ============================================================
    // 第三方接口访问错误（4000-4999）
    // 设计原则：
    //   4000-4009：通用第三方错误（所有厂商共用）
    //   4010-4019：微信相关（登录、支付、消息）
    //   4020-4029：支付宝相关
    //   4030-4039：短信/邮件服务商
    //   4040-4049：OSS/文件存储服务商
    //   4050-4059：地图/物流等
    //   4999：未分类的第三方错误兜底
    // ============================================================

    // ---------- 通用第三方错误（4000-4009） ----------
    THIRD_PARTY_ERROR(4000, "第三方服务异常，请稍后重试", HttpStatus.SERVICE_UNAVAILABLE),
    THIRD_PARTY_TIMEOUT(4001, "第三方服务响应超时", HttpStatus.GATEWAY_TIMEOUT),
    THIRD_PARTY_CONNECT_FAILED(4002, "第三方服务连接失败", HttpStatus.SERVICE_UNAVAILABLE),
    THIRD_PARTY_RATE_LIMITED(4003, "第三方服务调用频率超限", HttpStatus.TOO_MANY_REQUESTS),
    THIRD_PARTY_AUTH_FAILED(4004, "第三方服务认证失败", HttpStatus.UNAUTHORIZED),
    THIRD_PARTY_SIGN_ERROR(4005, "第三方接口签名验证失败", HttpStatus.BAD_REQUEST),
    THIRD_PARTY_PARAM_ERROR(4006, "第三方接口参数错误", HttpStatus.BAD_REQUEST),
    THIRD_PARTY_RESPONSE_PARSE_ERROR(4007, "第三方响应数据解析失败", HttpStatus.INTERNAL_SERVER_ERROR),
    THIRD_PARTY_UNKNOWN_ERROR(4008, "第三方未知错误", HttpStatus.INTERNAL_SERVER_ERROR),

    // ---------- 微信相关（4010-4019） ----------
    WECHAT_ERROR(4010, "微信服务异常", HttpStatus.SERVICE_UNAVAILABLE),
    WECHAT_CODE_INVALID(4011, "微信登录 code 无效或已过期", HttpStatus.BAD_REQUEST),
    WECHAT_AUTH_FAILED(4012, "微信授权失败", HttpStatus.UNAUTHORIZED),
    WECHAT_OPENID_NOT_FOUND(4013, "微信用户信息不存在", HttpStatus.NOT_FOUND),
    WECHAT_PAY_ERROR(4014, "微信支付调用失败", HttpStatus.SERVICE_UNAVAILABLE),
    WECHAT_PAY_SIGN_ERROR(4015, "微信支付签名验证失败", HttpStatus.BAD_REQUEST),
    WECHAT_PAY_ORDER_NOT_FOUND(4016, "微信支付订单不存在", HttpStatus.NOT_FOUND),
    WECHAT_REFUND_ERROR(4017, "微信退款失败", HttpStatus.SERVICE_UNAVAILABLE),
    WECHAT_REFUND_AMOUNT_ERROR(4018, "微信退款金额不合法", HttpStatus.BAD_REQUEST),

    // ---------- 支付宝相关（4020-4029） ----------
    ALIPAY_ERROR(4020, "支付宝服务异常", HttpStatus.SERVICE_UNAVAILABLE),
    ALIPAY_AUTH_FAILED(4021, "支付宝授权失败", HttpStatus.UNAUTHORIZED),
    ALIPAY_PAY_ERROR(4022, "支付宝支付调用失败", HttpStatus.SERVICE_UNAVAILABLE),
    ALIPAY_SIGN_ERROR(4023, "支付宝签名验证失败", HttpStatus.BAD_REQUEST),
    ALIPAY_REFUND_ERROR(4024, "支付宝退款失败", HttpStatus.SERVICE_UNAVAILABLE),
    ALIPAY_TRADE_NOT_FOUND(4025, "支付宝交易不存在", HttpStatus.NOT_FOUND),

    // ---------- 短信/邮件服务商（4030-4039） ----------
    SMS_SEND_FAILED(4030, "短信发送失败", HttpStatus.SERVICE_UNAVAILABLE),
    SMS_RATE_LIMITED(4031, "短信发送过于频繁", HttpStatus.TOO_MANY_REQUESTS),
    SMS_TEMPLATE_ERROR(4032, "短信模板不存在或未审核", HttpStatus.BAD_REQUEST),
    SMS_SIGN_ERROR(4033, "短信签名不合法", HttpStatus.BAD_REQUEST),
    SMS_BALANCE_INSUFFICIENT(4034, "短信服务商余额不足", HttpStatus.SERVICE_UNAVAILABLE),
    EMAIL_SEND_FAILED(4035, "邮件发送失败", HttpStatus.SERVICE_UNAVAILABLE),

    // ---------- OSS/文件存储（4040-4049） ----------
    OSS_UPLOAD_FAILED(4040, "文件上传失败", HttpStatus.SERVICE_UNAVAILABLE),
    OSS_DOWNLOAD_FAILED(4041, "文件下载失败", HttpStatus.SERVICE_UNAVAILABLE),
    OSS_DELETE_FAILED(4042, "文件删除失败", HttpStatus.SERVICE_UNAVAILABLE),
    OSS_FILE_NOT_FOUND(4043, "文件不存在", HttpStatus.NOT_FOUND),
    OSS_FILE_TOO_LARGE(4044, "文件大小超出限制", HttpStatus.BAD_REQUEST),
    OSS_FILE_TYPE_NOT_ALLOWED(4045, "文件类型不允许", HttpStatus.BAD_REQUEST),

    // ---------- 地图/物流等（4050-4059） ----------
    MAP_SERVICE_ERROR(4050, "地图服务异常", HttpStatus.SERVICE_UNAVAILABLE),
    MAP_GEOCODE_FAILED(4051, "地址解析失败", HttpStatus.BAD_REQUEST),
    LOGISTICS_QUERY_FAILED(4052, "物流信息查询失败", HttpStatus.SERVICE_UNAVAILABLE),
    LOGISTICS_COMPANY_NOT_FOUND(4053, "物流公司不存在", HttpStatus.NOT_FOUND),

    // ---------- 兜底 ----------
    THIRD_PARTY_UNDEFINED(4999, "未分类的第三方错误", HttpStatus.INTERNAL_SERVER_ERROR),

    // ----------通用加密解密工具错误（5000-5020----------
    ENCRYPT_ERROR(5000, "加密错误", HttpStatus.INTERNAL_SERVER_ERROR),
    DECRYPT_ERROR(5001, "解密错误", HttpStatus.INTERNAL_SERVER_ERROR),
    HASH_ERROR(5002, "哈希错误", HttpStatus.INTERNAL_SERVER_ERROR),

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