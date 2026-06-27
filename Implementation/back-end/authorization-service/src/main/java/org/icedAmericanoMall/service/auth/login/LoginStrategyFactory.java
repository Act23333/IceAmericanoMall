package org.icedAmericanoMall.service.auth.login;

import lombok.RequiredArgsConstructor;
import org.icedAmericanoMall.domain.enums.CredentialTypeEnum;
import org.icedAmericanoMall.domain.enums.IdentityTypeEnum;
import org.noLazy.common.enums.ErrorCode;
import org.noLazy.common.exception.BizException;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class LoginStrategyFactory {

    private final List<LoginStrategy> strategies;

    public LoginStrategy getStrategy(IdentityTypeEnum identityType, CredentialTypeEnum credentialType) {
        return strategies.stream()
                .filter(strategy -> strategy.support(identityType, credentialType))
                .findFirst()
                .orElseThrow(() -> new BizException(ErrorCode.PARAM_ERROR, "不支持的登录方式"));
    }
}
