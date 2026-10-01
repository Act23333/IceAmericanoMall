package org.icedamericanomall.common.exception;

import org.icedamericanomall.common.enums.ErrorCode;

public class WechatApiException extends  CommonException{
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