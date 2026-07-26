package org.noLazy.common.exception;

import org.noLazy.common.enums.ErrorCode;

/**
 * @ClassName: DBException
 * @Description: 数据库异常
 * @Author: noLazy
 * @Date: 2026/3/17 17:12
 * @Version: 1.0.0
 * @ProjectName: IcedAmericanoMall
 * @Package: org.noLazy.common.exception
 */
public class DBException extends CommonException {

    public DBException(ErrorCode errorCode) {
        super(errorCode);
    }

    public DBException(ErrorCode errorCode, String customMessage) {
        super(errorCode, customMessage);
    }
}
