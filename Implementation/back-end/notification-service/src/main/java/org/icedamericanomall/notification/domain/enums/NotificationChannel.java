package org.icedamericanomall.notification.domain.enums;

import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;

/**
 * 通知通道（领域枚举）。
 *
 * <p>取值与 ia-api 契约中的字符串一一对应（{@code NotificationSendReqCO} 不再重复携带通道字段，
 * 通道由 Feign 端点路径决定；本枚举负责把外部字符串解析成领域概念）。
 */
public enum NotificationChannel {

    /** 短信通道（阿里云 dysmsapi / Mock） */
    SMS("SMS", true),

    /** 邮件通道（SMTP / Mock） */
    EMAIL("EMAIL", true),

    /** 站内信通道（契约预留，尚未实现） */
    IN_APP("IN_APP", false),
    ;

    private final String code;

    /** 是否已实现投递能力 */
    private final boolean implemented;

    NotificationChannel(String code, boolean implemented) {
        this.code = code;
        this.implemented = implemented;
    }

    public String getCode() {
        return code;
    }

    public boolean isImplemented() {
        return implemented;
    }

    /**
     * 解析通道编码。
     *
     * @param code 通道编码（大小写不敏感）
     * @return 对应通道
     * @throws BizException 编码为空或非法（ErrorCode.PARAM_ERROR）
     */
    public static NotificationChannel of(String code) {
        if (code == null || code.isBlank()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "通知通道不能为空");
        }
        for (NotificationChannel channel : values()) {
            if (channel.code.equalsIgnoreCase(code.trim())) {
                return channel;
            }
        }
        throw new BizException(ErrorCode.PARAM_ERROR, "不支持的通知通道: " + code);
    }
}
