package org.icedamericanomall.common.exception;

import org.icedamericanomall.common.enums.ErrorCode;

/**
 * @ClassName: BadRequestException
 * @Description: 请求问题（如请求校验失败）
 * @Author: noLazy
 * @Date: 2026/3/17 15:06
 * @Version: 1.0.0
 * @ProjectName: IcedAmericanoMall
 * @Package: org.icedamericanomall.common.exception
 */
public class BadRequestException extends CommonException {

    public BadRequestException(ErrorCode errorCode) {
        super(errorCode);
    }

    public BadRequestException(ErrorCode errorCode, String customMessage) {
        super(errorCode, customMessage);
    }

    public BadRequestException(ErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }

    public BadRequestException(ErrorCode errorCode, String customMessage, Throwable cause) {
        super(errorCode, customMessage, cause);
    }

    public BadRequestException(ErrorCode errorCode, String customMessage, Object data) {
        super(errorCode, customMessage, data);
    }
}
