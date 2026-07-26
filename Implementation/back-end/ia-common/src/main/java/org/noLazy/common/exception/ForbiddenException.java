package org.noLazy.common.exception;

import org.noLazy.common.enums.ErrorCode;

/**
 * @ClassName: ForbiddenException
 * @Description: 禁止访问
 * @Author: noLazy
 * @Date: 2026/3/17 17:13
 * @Version: 1.0.0
 * @ProjectName: IcedAmericanoMall
 * @Package: org.noLazy.common.exception
 */
public class ForbiddenException extends CommonException {
    public ForbiddenException(ErrorCode errorCode) {
        super(errorCode);
    }

    public ForbiddenException(ErrorCode errorCode, String customMessage) {
        super(errorCode, customMessage);
    }
}
