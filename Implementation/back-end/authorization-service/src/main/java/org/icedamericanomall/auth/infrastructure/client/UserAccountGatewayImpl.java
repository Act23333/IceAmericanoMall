package org.icedamericanomall.auth.infrastructure.client;

import lombok.RequiredArgsConstructor;
import org.icedamericanomall.auth.domain.gateway.UserAccountGateway;
import org.icedamericanomall.client.UserClient;
import org.icedamericanomall.dto.UserAuthCO;
import org.icedamericanomall.common.enums.IdentityTypeEnum;
import org.springframework.stereotype.Component;

/**
 * 用户账号网关实现（防腐层）—— Feign 调 user-service，把外部 DTO 挡在领域层之外。
 *
 * <p>返回的是 {@code user.id}（技术主键 BIGINT），与 JWT claim / 网关 X-User-Id /
 * 其他服务 {@code user_id} 列的语义一致；user-service 的 {@code user_id}（UUID）
 * 仅是该上下文内部的业务唯一键。
 */
@Component
@RequiredArgsConstructor
public class UserAccountGatewayImpl implements UserAccountGateway {

    private final UserClient userClient;

    @Override
    public Long getOrCreateUserId(String identifier, IdentityTypeEnum identityType) {
        UserAuthCO request = UserAuthCO.builder()
                .identifier(identifier)
                .identityType(identityType)
                .build();
        return userClient.getOrCreateUser(request);
    }
}
