package org.icedamericanomall.verification.domain.model;

import java.time.Duration;

/**
 * 验证码策略（领域值对象，创建即校验）。
 *
 * <p>默认值 {@link #DEFAULT} 即业务约定：6 位数字、5 分钟有效、
 * 60 秒重发窗口、30 分钟内错误 5 次即作废、单目标全局日配额 10 条；可在 application.yml 的
 * {@code verification.code.*} 下覆盖（由基础设施层装配为 Bean 注入领域服务）。
 *
 * @param codeLength        验证码位数
 * @param codeTtl           验证码有效期
 * @param sendLimitWindow   重发限流窗口（同一目标在该窗口内只能签发一次）
 * @param maxFailures       允许的最大连续错误次数
 * @param failureWindow     错误计数窗口
 * @param dailySendCap      目标当日发送上限（<b>跨场景共享</b>，ADR-051 修订；短信轰炸成本控制）
 * @param dailyWindow       日配额滚动窗口
 */
public record VerificationPolicy(int codeLength,
                                 Duration codeTtl,
                                 Duration sendLimitWindow,
                                 int maxFailures,
                                 Duration failureWindow,
                                 int dailySendCap,
                                 Duration dailyWindow) {

    /** 业务默认策略（6 位数字 / 5 分钟 / 60 秒重发 / 30 分钟 5 次作废 / 日配额 10）。 */
    public static final VerificationPolicy DEFAULT = new VerificationPolicy(
            6, Duration.ofMinutes(5), Duration.ofSeconds(60), 5, Duration.ofMinutes(30),
            10, Duration.ofHours(24));

    /** 紧凑构造器：非法参数直接拒绝，避免运行期出现「TTL 为负」这类隐性缺陷。 */
    public VerificationPolicy {
        if (codeLength < 4 || codeLength > 9) {
            throw new IllegalArgumentException("验证码位数必须在 4-9 之间");
        }
        if (codeTtl == null || codeTtl.isNegative() || codeTtl.isZero()) {
            throw new IllegalArgumentException("验证码有效期必须为正");
        }
        if (sendLimitWindow == null || sendLimitWindow.isNegative()) {
            throw new IllegalArgumentException("重发限流窗口不能为负");
        }
        if (maxFailures < 1) {
            throw new IllegalArgumentException("最大错误次数必须大于 0");
        }
        if (failureWindow == null || failureWindow.isNegative() || failureWindow.isZero()) {
            throw new IllegalArgumentException("错误计数窗口必须为正");
        }
        if (dailySendCap < 1) {
            throw new IllegalArgumentException("日发送配额必须大于 0");
        }
        if (dailyWindow == null || dailyWindow.isNegative() || dailyWindow.isZero()) {
            throw new IllegalArgumentException("日配额窗口必须为正");
        }
    }
}
