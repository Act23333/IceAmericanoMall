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
 *     ttl-seconds: 300          # 验证码有效期（5 分钟）
 *     send-limit-seconds: 60    # 重发限流窗口（60 秒，按场景隔离）
 *     max-failures: 5           # 连续错误上限，超过即作废验证码
 *     failure-window-seconds: 1800
 *     daily-send-cap: 10        # 目标当日发送上限（跨场景共享，短信轰炸成本控制，ADR-051 修订）
 *     daily-window-seconds: 86400
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

    /** 目标当日发送上限（跨场景共享的全局配额，ADR-051 修订） */
    private int dailySendCap = 10;

    /** 日配额滚动窗口（秒） */
    private long dailyWindowSeconds = 86400;

    /** 转换为领域策略对象。 */
    public org.icedamericanomall.verification.domain.model.VerificationPolicy toPolicy() {
        return new org.icedamericanomall.verification.domain.model.VerificationPolicy(
                length,
                Duration.ofSeconds(ttlSeconds),
                Duration.ofSeconds(sendLimitSeconds),
                maxFailures,
                Duration.ofSeconds(failureWindowSeconds),
                dailySendCap,
                Duration.ofSeconds(dailyWindowSeconds));
    }
}
