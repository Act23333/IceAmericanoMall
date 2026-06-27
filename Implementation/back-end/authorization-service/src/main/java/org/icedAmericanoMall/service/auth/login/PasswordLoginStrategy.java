package org.icedAmericanoMall.service.auth.login;

import lombok.RequiredArgsConstructor;
import org.icedAmericanoMall.client.UserClient;
import org.icedAmericanoMall.domain.dto.OAuth2TokenResp;
import org.icedAmericanoMall.domain.dto.auth.LoginReq;
import org.icedAmericanoMall.domain.enums.CredentialTypeEnum;
import org.icedAmericanoMall.domain.enums.IdentityTypeEnum;
import org.icedAmericanoMall.dto.LoginRespDTO;
import org.icedAmericanoMall.dto.PasswordLoginReqDTO;
import org.noLazy.common.enums.ErrorCode;
import org.noLazy.common.exception.BizException;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PasswordLoginStrategy implements LoginStrategy {

    private static final String PHONE_PATTERN = "^1[3-9]\\d{9}$";
    private static final String USERNAME_PATTERN = "^[a-zA-Z][a-zA-Z0-9_]{3,19}$";
    private static final String PASSWORD_PATTERN =
            "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[!@#$%^&*])[0-9a-zA-Z!@#$%^&*]{8,20}$";

    private final UserClient userClient;
    private final LoginTokenService loginTokenService;

    @Override
    public boolean support(IdentityTypeEnum identityType, CredentialTypeEnum credentialType) {
        return credentialType == CredentialTypeEnum.PASSWORD
                && (identityType == IdentityTypeEnum.USERNAME || identityType == IdentityTypeEnum.PHONE);
    }

    @Override
    public OAuth2TokenResp login(LoginReq request) {
        PasswordLoginReqDTO passwordReq = buildPasswordRequest(request);
        LoginRespDTO userResp = userClient.loginByPassword(passwordReq);
        return loginTokenService.createLoginResponse(userResp);
    }

    private PasswordLoginReqDTO buildPasswordRequest(LoginReq request) {
        validatePassword(request.getCredential());
        PasswordLoginReqDTO passwordReq = new PasswordLoginReqDTO();
        passwordReq.setPassword(request.getCredential());
        if (request.getIdentityType() == IdentityTypeEnum.USERNAME) {
            validateAccount(request.getAccount(), USERNAME_PATTERN, "用户名格式不正确");
            passwordReq.setUsername(request.getAccount());
            return passwordReq;
        }
        if (request.getIdentityType() == IdentityTypeEnum.PHONE) {
            validateAccount(request.getAccount(), PHONE_PATTERN, "手机号格式不正确");
            passwordReq.setPhone(request.getAccount());
            return passwordReq;
        }
        throw new BizException(ErrorCode.PARAM_ERROR, "不支持的密码登录身份类型");
    }

    private void validatePassword(String password) {
        validateAccount(password, PASSWORD_PATTERN, "密码格式不正确");
    }

    private void validateAccount(String value, String pattern, String message) {
        if (!value.matches(pattern)) {
            throw new BizException(ErrorCode.PARAM_ERROR, message);
        }
    }
}
