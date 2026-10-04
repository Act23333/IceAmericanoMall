package org.icedamericanomall.integration;

import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.CommonException;

/**
 * 微信开放接口异常（厂商异常，随第三方集成能力一同归属 ia-integration）。
 *
 * <p><b>V5.3 迁移</b>：原先位于 {@code org.icedamericanomall.common.exception}（内核）。
 * 但「微信」是厂商细节，共享内核不应认识任何具体服务商 —— 内核只保留
 * {@code CommonException} 层级与通用 {@code ErrorCode}（4000-4099 区间已按厂商分段）。
 * 唯一消费方 {@code RealWechatMiniClient} 与本类同模块，迁移不影响任何调用方语义：
 * 本类仍继承 {@code CommonException}，业务服务的 {@code GlobalExceptionHandler} 照常处理。
 *
 * <p>抛出语义：微信返回的 {@code errcode/errmsg} 无法满足调用诉求时抛出，
 * 携带 {@code WECHAT_*} 段的 {@link ErrorCode}。
 */
public class WechatApiException extends CommonException {

    public WechatApiException(ErrorCode errorCode) {
        super(errorCode);
    }

    public WechatApiException(ErrorCode errorCode, String customMessage) {
        super(errorCode, customMessage);
    }

    public WechatApiException(ErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }

    public WechatApiException(ErrorCode errorCode, String customMessage, Object data) {
        super(errorCode, customMessage, data);
    }

    public WechatApiException(ErrorCode errorCode, String customMessage, Throwable cause, Object data) {
        super(errorCode, customMessage, cause, data);
    }

    public WechatApiException(ErrorCode errorCode, String msg, Throwable cause) {
        super(errorCode, msg, cause);
    }
}
