package org.icedamericanomall.verification.app.command;

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
}
