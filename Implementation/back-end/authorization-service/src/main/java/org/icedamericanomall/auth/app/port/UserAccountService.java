package org.icedamericanomall.auth.app.port;

import org.icedamericanomall.auth.app.command.RegisterCommand;
import org.icedamericanomall.auth.app.command.ResetPasswordCommand;

/**
 * 用户账号写操作能力（应用层端口 —— 应用层编排需要，基础设施层用 Feign 调 user-service）。
 *
 * <p>入参为应用层命令，返回本上下文的语义（userId），Feign DTO 的转换收敛在基础设施层。
 */
public interface UserAccountService {

    /**
     * 注册用户（含短信验证码校验，由 user-service 负责具体落地）。
     *
     * @param command 注册命令
     * @return 新用户 ID
     */
    Long register(RegisterCommand command);

    /**
     * 重置密码（短信验证码 + 新密码）。
     *
     * @param command 重置密码命令
     */
    void resetPassword(ResetPasswordCommand command);
}
