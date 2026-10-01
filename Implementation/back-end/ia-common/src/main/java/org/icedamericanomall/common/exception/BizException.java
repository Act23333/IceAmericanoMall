package org.icedamericanomall.common.exception;

import org.icedamericanomall.common.enums.ErrorCode;

/**
 * @ClassName: BizException
 * @Description: 业务异常
 * @Author: noLazy
 * @Date: 2026/3/17 15:42
 * @Version: 1.0.0
 * @ProjectName: IcedAmericanoMall
 * @Package: org.icedamericanomall.common.exception
 */
// 业务异常
public class BizException extends CommonException {
    public BizException(ErrorCode errorCode) {
        super(errorCode);
    }

    public BizException(ErrorCode errorCode, String customMessage) {
        super(errorCode, customMessage);
    }

    public BizException(ErrorCode errorCode, String customMessage, Object data) {
        super(errorCode, customMessage, data);
    }
}