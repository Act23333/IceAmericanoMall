package org.icedamericanomall.verification.app.command;

import java.util.Locale;

/**
 * 验证码签发命令（应用层入参）。
 *
 * <p>通道以原始字符串传入（来自 Feign DTO），由用例编排器解析为领域枚举，
 * 解析失败返回 {@code PARAM_INVALID} 结果而不是抛异常（见 {@code VerificationOutcome}）。
 *
 * @param channel   通道编码（SMS / EMAIL）
 * @param target    目标（手机号 / 邮箱）
 * @param scene     业务场景
 * @param requestId 幂等 / 链路追踪标识
 */
public record CodeIssueCommand(String channel, String target, String scene, String requestId) {

    /**
     * 紧凑构造器：目标归一化（自封装）。
     *
     * <p>邮箱大小写不敏感，统一 {@code trim + lowercase}（与登录侧 authorization-service
     * 的 {@code EmailAddress} 值对象同源语义），保证验证码在「公开端点发码 ↔ 内部端点校验」
     * 两条链路命中同一 Redis Key（本服务私有键空间）；手机号仅 trim。
     */
    public CodeIssueCommand {
        if (target != null) {
            target = target.trim();
            if ("EMAIL".equalsIgnoreCase(channel)) {
                target = target.toLowerCase(Locale.ROOT);
            }
        }
        if (scene != null) {
            scene = scene.trim();
        }
    }
}
