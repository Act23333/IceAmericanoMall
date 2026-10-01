package org.icedamericanomall.verification.infrastructure.constant;

import org.icedamericanomall.verification.domain.enums.VerificationChannel;

import java.time.Duration;

/**
 * 验证码 Redis Key 契约（基础设施层常量）。
 *
 * <p><b>跨服务契约（R5，不可随意变更）</b>：
 * <ul>
 *   <li>{@code sms:code:{phone}} —— TTL 5 分钟。
 *       <b>必须与 user-service 直发链路的
 *       {@code org.icedamericanomall.constants.RedisKeyConstants.SMS_CODE_PREFIX} 完全一致</b>，
 *       否则「user-service 发码 + verification-service 校验」（或反之）会互相看不见验证码。</li>
 *   <li>{@code sms:limit:{phone}} —— 60 秒重发窗口，同样与 user-service 保持一致</li>
 *   <li>{@code email:code:{email}} / {@code email:limit:{email}} —— 邮件通道，沿用同一命名规则</li>
 *   <li>{@code sms:fail:{phone}} / {@code email:fail:{email}} —— 本服务新增的失败计数 Key（30 分钟窗口）</li>
 * </ul>
 */
public final class VerificationRedisKeys {

    /** 短信验证码前缀（与 user-service 的 RedisKeyConstants.SMS_CODE_PREFIX 一致） */
    public static final String SMS_CODE_PREFIX = "sms:code:";

    /** 短信重发限流前缀（与 user-service 的 RedisKeyConstants.SMS_LIMIT_PREFIX 一致） */
    public static final String SMS_LIMIT_PREFIX = "sms:limit:";

    /** 邮件验证码前缀 */
    public static final String EMAIL_CODE_PREFIX = "email:code:";

    /** 邮件重发限流前缀 */
    public static final String EMAIL_LIMIT_PREFIX = "email:limit:";

    /** 短信错误计数前缀（本服务新增） */
    public static final String SMS_FAIL_PREFIX = "sms:fail:";

    /** 邮件错误计数前缀（本服务新增） */
    public static final String EMAIL_FAIL_PREFIX = "email:fail:";

    /** 验证码有效期（与 user-service 写入时的 TTL 保持一致：5 分钟） */
    public static final Duration CODE_TTL = Duration.ofMinutes(5);

    /** 重发限流窗口（与 user-service 保持一致：60 秒） */
    public static final Duration LIMIT_TTL = Duration.ofSeconds(60);

    /** 错误计数窗口（30 分钟） */
    public static final Duration FAILURE_TTL = Duration.ofMinutes(30);

    private VerificationRedisKeys() {
    }

    /** 验证码 Key。 */
    public static String codeKey(VerificationChannel channel, String target) {
        return prefix(channel, SMS_CODE_PREFIX, EMAIL_CODE_PREFIX) + target;
    }

    /** 重发限流 Key。 */
    public static String limitKey(VerificationChannel channel, String target) {
        return prefix(channel, SMS_LIMIT_PREFIX, EMAIL_LIMIT_PREFIX) + target;
    }

    /** 错误计数 Key。 */
    public static String failureKey(VerificationChannel channel, String target) {
        return prefix(channel, SMS_FAIL_PREFIX, EMAIL_FAIL_PREFIX) + target;
    }

    private static String prefix(VerificationChannel channel, String smsPrefix, String emailPrefix) {
        return channel == VerificationChannel.SMS ? smsPrefix : emailPrefix;
    }
}
