package org.noLazy.common.exception;

import org.noLazy.common.enums.ErrorCode;

/**
 * @ClassName: BizException
 * @Description: 业务异常
 * @Author: noLazy
 * @Date: 2026/3/17 15:42
 * @Version: 1.0.0
 * @ProjectName: IcedAmericanoMall
 * @Package: org.noLazy.common.exception
 */
// 业务异常
public class BizException extends CommonException {
    public BizException(ErrorCode errorCode) {
        super(errorCode);
    }

    public BizException(ErrorCode errorCode, String customMessage) {
        super(errorCode, customMessage);
    }
}