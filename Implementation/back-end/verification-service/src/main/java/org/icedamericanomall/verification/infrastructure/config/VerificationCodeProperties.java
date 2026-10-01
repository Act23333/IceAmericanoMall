package org.icedamericanomall.verification.infrastructure.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 验证码策略配置（{@code verification.code.*}）。
 *
 * <p>默认值与 {@link org.icedamericanomall.verification.domain.model.VerificationPolicy#DEFAULT}
 * 保持一致；调整策略无需改代码，只改配置。
 *
 * <pre>
 * verification:
 *   code:
 *     length: 6
 *     ttl-seconds: 300          # 验证码有效期（与 user-service 直发链路一致：5 分钟）
 *     send-limit-seconds: 60    # 重发限流窗口（与 user-service 一致：60 秒）
 *     max-failures: 5           # 连续错误上限，超过即作废验证码
 *     failure-window-seconds: 1800
 * </pre>
 */
@Data
@Component
@ConfigurationProperties(prefix = "verification.code")
public class VerificationCodeProperties {

    /** 验证码位数 */
    private int length = 6;

    /** 验证码有效期（秒） */
    private long ttlSeconds = 300;

    /** 重发限流窗口（秒） */
    private long sendLimitSeconds = 60;

    /** 最大错误次数 */
    private int maxFailures = 5;

    /** 错误计数窗口（秒） */
    private long failureWindowSeconds = 1800;

    /** 转换为领域策略对象。 */
    public org.icedamericanomall.verification.domain.model.VerificationPolicy toPolicy() {
        return new org.icedamericanomall.verification.domain.model.VerificationPolicy(
                length,
                Duration.ofSeconds(ttlSeconds),
                Duration.ofSeconds(sendLimitSeconds),
                maxFailures,
                Duration.ofSeconds(failureWindowSeconds));
    }
}
