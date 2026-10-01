package org.icedamericanomall.verification.domain.enums;

import java.util.Optional;

/**
 * 验证通道（领域枚举）。
 *
 * <p>取值与 ia-api 契约字符串一致（{@code SMS} / {@code EMAIL}）；
 * Redis Key 布局<b>不</b>在本枚举内拼接（属基础设施细节），
 * 由 {@code infrastructure/repository/VerificationRedisKeys} 统一承担。
 */
public enum VerificationChannel {

    /** 短信通道：验证码写入 {@code sms:code:{target}}，与 user-service 直发链路互通 */
    SMS("SMS"),

    /** 邮件通道：验证码写入 {@code email:code:{target}} */
    EMAIL("EMAIL"),
    ;

    private final String code;

    VerificationChannel(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    /**
     * 宽松解析（大小写不敏感、忽略首尾空格），解析失败返回空。
     *
     * @param code 通道编码
     * @return 通道，或 {@link Optional#empty()}
     */
    public static Optional<VerificationChannel> find(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        String normalized = code.trim();
        for (VerificationChannel channel : values()) {
            if (channel.code.equalsIgnoreCase(normalized)) {
                return Optional.of(channel);
            }
        }
        return Optional.empty();
    }
}
