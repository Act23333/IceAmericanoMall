package org.icedamericanomall.common.exception;

import org.icedamericanomall.common.enums.ErrorCode;

public class HttpException extends CommonException {

    public HttpException(ErrorCode errorCode, String responseBody, Throwable cause) {
        super(errorCode, responseBody, cause);
    }

    public HttpException(ErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }

}