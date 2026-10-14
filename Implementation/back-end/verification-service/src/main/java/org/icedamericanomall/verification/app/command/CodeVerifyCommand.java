package org.icedamericanomall.verification.app.command;

/**
 * 验证码校验命令（应用层入参）。
 *
 * @param channel 通道编码（SMS / EMAIL）
 * @param target  目标（手机号 / 邮箱）
 * @param code    用户提交的验证码
 */
public record CodeVerifyCommand(String channel, String scene, String target, String code) {
}
