package org.icedamericanomall.service.auth.login;

import org.icedamericanomall.domain.dto.OAuth2TokenResp;
import org.icedamericanomall.domain.dto.auth.LoginReq;
import org.icedamericanomall.domain.enums.CredentialTypeEnum;
import org.icedamericanomall.domain.enums.IdentityTypeEnum;

public interface LoginStrategy {

    boolean support(IdentityTypeEnum identityType, CredentialTypeEnum credentialType);

    OAuth2TokenResp login(LoginReq request);
}
