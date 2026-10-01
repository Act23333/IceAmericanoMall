package org.icedamericanomall.auth.infrastructure.client;

import lombok.RequiredArgsConstructor;
import org.icedamericanomall.auth.app.command.RegisterCommand;
import org.icedamericanomall.auth.app.command.ResetPasswordCommand;
import org.icedamericanomall.auth.app.port.UserAccountService;
import org.icedamericanomall.auth.domain.gateway.UserAccountGateway;
import org.icedamericanomall.client.AuthClient;
import org.icedamericanomall.dto.RegisterReqCO;
import org.icedamericanomall.dto.ResetPasswordReqCO;
import org.icedamericanomall.common.enums.IdentityTypeEnum;
import org.springframework.stereotype.Component;

/**
 * 用户账号写操作实现（基础设施层）—— Feign 调 user-service 的 /internal/auth。
 *
 * <p>注意：user-service 的 {@code LoginRespCO.userId} 是其内部的业务唯一键（UUID 字符串），
 * 而本上下文（JWT claim、网关 X-User-Id、其他服务 user_id 列）统一使用技术主键 Long id，
 * 因此注册成功后按手机号回查一次（幂等接口）。
 */
@Component
@RequiredArgsConstructor
public class UserAccountServiceImpl implements UserAccountService {

    private final AuthClient authClient;
    private final UserAccountGateway userAccountGateway;

    @Override
    public Long register(RegisterCommand command) {
        RegisterReqCO request = new RegisterReqCO();
        request.setUsername(command.username());
        request.setPhone(command.phone());
        request.setPassword(command.password());
        request.setCode(command.code());
        request.setDeviceId(command.deviceId());
        authClient.register(request);
        return userAccountGateway.getOrCreateUserId(command.phone(), IdentityTypeEnum.PHONE);
    }

    @Override
    public void resetPassword(ResetPasswordCommand command) {
        ResetPasswordReqCO request = new ResetPasswordReqCO();
        request.setPhone(command.phone());
        request.setCode(command.code());
        request.setNewPassword(command.newPassword());
        authClient.resetPassword(request);
    }
}
