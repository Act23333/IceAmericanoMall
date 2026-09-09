package org.icedamericanomall.service.auth.login;

import lombok.RequiredArgsConstructor;
import org.icedamericanomall.client.AuthClient;
import org.icedamericanomall.domain.dto.OAuth2TokenResp;
import org.icedamericanomall.domain.dto.auth.LoginReq;
import org.icedamericanomall.domain.enums.CredentialTypeEnum;
import org.icedamericanomall.domain.enums.IdentityTypeEnum;
import org.icedamericanomall.dto.LoginRespDTO;
import org.icedamericanomall.dto.SmsLoginReqDTO;
import org.noLazy.common.enums.ErrorCode;
import org.noLazy.common.exception.BizException;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SmsLoginStrategy implements LoginStrategy {

    private static final String PHONE_PATTERN = "^1[3-9]\\d{9}$";
    private static final String SMS_CODE_PATTERN = "^\\d{6}$";

    private final AuthClient authClient;
    private final LoginTokenService loginTokenService;

    @Override
    public boolean support(IdentityTypeEnum identityType, CredentialTypeEnum credentialType) {
        return identityType == IdentityTypeEnum.PHONE && credentialType == CredentialTypeEnum.SMS_CODE;
    }

    @Override
    public OAuth2TokenResp login(LoginReq request) {
        validatePhoneIdentity(request);
        SmsLoginReqDTO smsReq = new SmsLoginReqDTO();
        smsReq.setPhone(request.getAccount());
        smsReq.setCode(request.getCredential());
        LoginRespDTO userResp = authClient.loginBySms(smsReq);
        return loginTokenService.createLoginResponse(userResp);
    }

    private void validatePhoneIdentity(LoginReq request) {
        if (request.getIdentityType() != IdentityTypeEnum.PHONE) {
            throw new BizException(ErrorCode.PARAM_ERROR, "短信登录必须使用手机号身份");
        }
        if (!request.getAccount().matches(PHONE_PATTERN)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "手机号格式不正确");
        }
        if (!request.getCredential().matches(SMS_CODE_PATTERN)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "验证码格式不正确");
        }
    }
}
