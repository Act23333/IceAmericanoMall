package org.icedamericanomall.notification.domain.enums;

/**
 * 通知通道（领域枚举）。
 *
 * <p>取值与 ia-api 契约字符串一一对应。通道由 Feign 端点路径决定
 * （{@code /internal/notify/sms}、{@code /internal/notify/email}），
 * 本枚举仅用于表达/记录通道概念（如能力标记）。
 */
public enum NotificationChannel {

    /** 短信通道（阿里云 dysmsapi / Mock） */
    SMS("SMS", true),

    /** 邮件通道（SMTP / Mock） */
    EMAIL("EMAIL", true),

    /** 站内信通道（已实现：走 {@code notification} 表落库，不经通道投递） */
    IN_APP("IN_APP", true),
    ;

    private final String code;

    /** 是否已具备投递/存储能力 */
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
}
