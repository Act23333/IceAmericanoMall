package org.icedAmericanoMall.service.auth.login;

import org.icedAmericanoMall.domain.dto.OAuth2TokenResp;
import org.icedAmericanoMall.domain.dto.auth.LoginReq;
import org.icedAmericanoMall.domain.enums.CredentialTypeEnum;
import org.icedAmericanoMall.domain.enums.IdentityTypeEnum;
import org.noLazy.common.enums.ErrorCode;
import org.noLazy.common.exception.BizException;
import org.springframework.stereotype.Component;

@Component
public class EmailPasswordLoginStrategy implements LoginStrategy {

    @Override
    public boolean support(IdentityTypeEnum identityType, CredentialTypeEnum credentialType) {
        return identityType == IdentityTypeEnum.EMAIL && credentialType == CredentialTypeEnum.PASSWORD;
    }

    @Override
    public OAuth2TokenResp login(LoginReq request) {
        throw new BizException(ErrorCode.PARAM_ERROR, "邮箱密码登录尚未接入用户服务");
    }
}
