package org.icedamericanomall.user.domain.exception;

import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;

/**
 * 用户业务唯一键冲突（uk_phone / uk_wx_openid / uk_user_id）。
 *
 * <p>由基础设施层把持久化框架的 {@code DuplicateKeyException} 翻译成本异常，
 * 使领域层不必依赖 Spring 的异常类型。并发创建时领域服务捕获本异常并回查（幂等）。
 */
public class DuplicateUserException extends BizException {

    public DuplicateUserException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
