package org.icedamericanomall.user.infrastructure.constant;

/**
 * 本服务涉及的跨服务 Redis Key 契约（基础设施层常量）。
 *
 * <p>{@code sms:code:{phone}} 与 verification-service 的
 * {@code VerificationRedisKeys.SMS_CODE_PREFIX} 完全一致（发码迁移后由
 * verification-service 签写、本服务灰度路径读取，ADR-016）；
 * 60 秒重发窗口（旧 {@code sms:limit:} 本地限流）已随迁移交还 verification-service。
 */
public class RedisKeyConstants {

    /** 短信验证码前缀（与 verification-service 契约一致，勿改） */
    public static final String SMS_CODE_PREFIX = "sms:code:";
}
