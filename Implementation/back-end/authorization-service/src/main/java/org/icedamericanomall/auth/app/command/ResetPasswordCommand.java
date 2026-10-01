package org.icedamericanomall.auth.app.command;

import lombok.Builder;

/**
 * 重置密码命令 —— 应用层写操作入参。
 *
 * @param phone       手机号
 * @param code        短信验证码
 * @param newPassword 新密码明文
 */
@Builder
public record ResetPasswordCommand(
        String phone,
        String code,
        String newPassword
) {
}
