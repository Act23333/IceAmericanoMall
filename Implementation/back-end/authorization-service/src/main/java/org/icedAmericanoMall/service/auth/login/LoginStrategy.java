package org.icedAmericanoMall.service.auth.login;

import org.icedAmericanoMall.domain.dto.OAuth2TokenResp;
import org.icedAmericanoMall.domain.dto.auth.LoginReq;
import org.icedAmericanoMall.domain.enums.CredentialTypeEnum;
import org.icedAmericanoMall.domain.enums.IdentityTypeEnum;

public interface LoginStrategy {

    boolean support(IdentityTypeEnum identityType, CredentialTypeEnum credentialType);

    OAuth2TokenResp login(LoginReq request);
}
