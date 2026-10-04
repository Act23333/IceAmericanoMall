package org.icedamericanomall.verification.infrastructure.constant;

import org.icedamericanomall.verification.domain.enums.VerificationChannel;

import java.time.Duration;

/**
 * 验证码 Redis Key 布局（基础设施层常量）。
 *
 * <p><b>私有键空间</b>：本服务是验证码的唯一读写方，所有 Key 均在
 * {@code verification-service} 私有键空间内，<b>不再跨服务共享</b>
 * （旧的「user-service 直发链路共享 {@code sms:code:}」契约已随 ADR-016 收尾解散，
 * 其他服务只能经本服务的 Feign 端点签发/校验，不得直读 Redis）。
 *
 * <ul>
 *   <li>{@code sms:code:{phone}} / {@code email:code:{email}} —— 验证码，TTL 5 分钟</li>
 *   <li>{@code sms:limit:{phone}} / {@code email:limit:{email}} —— 60 秒重发窗口</li>
 *   <li>{@code sms:fail:{phone}} / {@code email:fail:{email}} —— 失败计数 Key（30 分钟窗口）</li>
 * </ul>
 */
public final class VerificationRedisKeys {

    /** 短信验证码前缀（本服务私有键空间） */
    public static final String SMS_CODE_PREFIX = "sms:code:";

    /** 短信重发限流前缀（本服务私有键空间） */
    public static final String SMS_LIMIT_PREFIX = "sms:limit:";

    /** 邮件验证码前缀（本服务私有键空间） */
    public static final String EMAIL_CODE_PREFIX = "email:code:";

    /** 邮件重发限流前缀（本服务私有键空间） */
    public static final String EMAIL_LIMIT_PREFIX = "email:limit:";

    /** 短信错误计数前缀（本服务私有键空间） */
    public static final String SMS_FAIL_PREFIX = "sms:fail:";

    /** 邮件错误计数前缀（本服务私有键空间） */
    public static final String EMAIL_FAIL_PREFIX = "email:fail:";

    /** 验证码有效期（5 分钟） */
    public static final Duration CODE_TTL = Duration.ofMinutes(5);

    /** 重发限流窗口（60 秒） */
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
