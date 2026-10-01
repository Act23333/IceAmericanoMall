package org.icedamericanomall.common.exception;

import lombok.Getter;
import org.icedamericanomall.common.enums.ErrorCode;

/**
 * @ClassName: CommonException
 * @Description: TODO
 * @Author: noLazy
 * @Date: 2026/3/17 15:04
 * @Version: 1.0.0
 * @ProjectName: IcedAmericanoMall
 * @Package: org.icedamericanomall.common.exception
 */
@Getter
public class CommonException extends RuntimeException{
    private final ErrorCode errorCode;      // 错误码枚举
    private final String customMessage;     // 可选的自定义消息
    private final Object data;


    public CommonException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.data = null;
        this.customMessage = null;
    }

    public CommonException(ErrorCode errorCode, String customMessage) {
        super(customMessage);
        this.errorCode = errorCode;
        this.customMessage = customMessage;
        this.data = null;
    }

    public CommonException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode;
        this.data = null;
        this.customMessage = null;
    }
    public CommonException(ErrorCode errorCode, String customMessage, Object data) {
        super(customMessage);
        this.errorCode = errorCode;
        this.customMessage = customMessage;
        this.data = data;
    }
    public CommonException(ErrorCode errorCode, String customMessage, Throwable cause, Object data) {
        super(customMessage, cause);
        this.errorCode = errorCode;
        this.customMessage = customMessage;
        this.data = data;
    }

    /**
     * 获取最终展示给用户的消息（优先使用自定义消息）
     */
    public String getDisplayMessage() {
        return customMessage != null ? customMessage : errorCode.getMessage();
    }


}
