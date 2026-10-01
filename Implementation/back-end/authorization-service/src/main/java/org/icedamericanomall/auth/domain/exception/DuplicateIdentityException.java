package org.icedamericanomall.auth.domain.exception;

import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;

/**
 * 认证标识已存在 —— 唯一索引（identityType + 业务标识）冲突。
 *
 * <p>由基础设施层把持久化框架的冲突异常翻译成本异常，
 * 使领域层不必依赖 Spring 的 {@code DuplicateKeyException}。
 */
public class DuplicateIdentityException extends BizException {

    public DuplicateIdentityException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
