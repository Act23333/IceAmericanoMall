package org.noLazy.common.exception;

import org.noLazy.common.enums.ErrorCode;

/**
 * @ClassName: UnauthorizedException
 * @Description: 无权限访问，鉴权失败
 * @Author: noLazy
 * @Date: 2026/3/17 17:15
 * @Version: 1.0.0
 * @ProjectName: IcedAmericanoMall
 * @Package: org.noLazy.common.exception
 */
public class UnauthorizedException extends CommonException {
    public UnauthorizedException(ErrorCode errorCode) {
        super(errorCode);
    }

    public UnauthorizedException(ErrorCode errorCode, String customMessage) {
        super(errorCode, customMessage);
    }
}
