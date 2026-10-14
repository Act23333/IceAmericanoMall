package org.icedamericanomall.auth.domain.enums;

import lombok.Getter;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;

/**
 * 验证通道（领域枚举）—— 取值与 verification-service 的契约字符串一致（{@code SMS} / {@code EMAIL}）。
 *
 * <p>本上下文（authorization-service）不自己生成验证码：签发与校验统一委托
 * verification-service（唯一出口）。本枚举只表达"这条验证码走哪条通道"，
 * 用于调用其 {@code /internal/verification/code/verify} 时指定通道。
 *
 * <p>注意：通道与身份类型不是同一个概念（{@code PHONE} 走 {@code SMS}，{@code EMAIL} 走 {@code EMAIL}），
 * 因此不复用 {@code IdentityTypeEnum}。
 */
@Getter
public enum VerificationChannelEnum {

    /** 短信通道：{@code sms:code:{scene}:{phone}}（场景隔离码池，ADR-051） */
    SMS("SMS", "短信"),

    /** 邮件通道：{@code email:code:{scene}:{email}}（场景隔离码池，ADR-051） */
    EMAIL("EMAIL", "邮件"),
    ;

    private final String code;
    private final String desc;

    VerificationChannelEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 宽松解析通道编码（大小写不敏感、忽略首尾空格）。
     *
     * @param code 通道编码（如 {@code SMS}）
     * @return 匹配的通道
     * @throws BizException 编码为空或不被支持
     */
    public static VerificationChannelEnum fromCode(String code) {
        if (code != null && !code.isBlank()) {
            String normalized = code.trim();
            for (VerificationChannelEnum channel : values()) {
                if (channel.code.equalsIgnoreCase(normalized)) {
                    return channel;
                }
            }
        }
        throw new BizException(ErrorCode.PARAM_ERROR, "不支持的验证通道: " + code);
    }
}
