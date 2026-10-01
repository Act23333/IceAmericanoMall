package org.icedamericanomall.common.exception;

import org.icedamericanomall.common.enums.ErrorCode;

/**
 * @ClassName: CryptoException
 * @Description: TODO
 * @Author: noLazy
 * @Date: 2026/9/23 16:44
 * @Version: 1.0.0
 * @ProjectName: back-end
 * @Package: org.icedamericanomall.common.exception
 */
public class CryptoException extends CommonException{
    public CryptoException(ErrorCode errorCode) {
        super(errorCode);
    }

    public CryptoException(ErrorCode errorCode, String customMessage) {
        super(errorCode, customMessage);
    }

    public CryptoException(ErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }

    public CryptoException(ErrorCode errorCode, String customMessage, Object data) {
        super(errorCode, customMessage, data);
    }

    public CryptoException(ErrorCode errorCode, String customMessage, Throwable cause, Object data) {
        super(errorCode, customMessage, cause, data);
    }
}
