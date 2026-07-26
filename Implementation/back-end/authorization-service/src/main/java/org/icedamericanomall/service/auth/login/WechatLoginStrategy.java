package org.icedamericanomall.service.auth.login;

import lombok.RequiredArgsConstructor;
import org.icedamericanomall.client.AuthClient;
import org.icedamericanomall.domain.dto.OAuth2TokenResp;
import org.icedamericanomall.domain.dto.auth.LoginReq;
import org.icedamericanomall.domain.enums.CredentialTypeEnum;
import org.icedamericanomall.domain.enums.IdentityTypeEnum;
import org.icedamericanomall.dto.LoginRespDTO;
import org.icedamericanomall.dto.WechatLoginReqDTO;
import org.noLazy.common.enums.ErrorCode;
import org.noLazy.common.exception.BizException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 微信 OAuth 登录策略 —— identityType=WECHAT + credentialType=OAUTH_TOKEN。
 * {@code credential} 承载前端授权回调拿到的微信 code；委托 user-service 完成 code→openid 与查/建用户。
 */
@Component
@RequiredArgsConstructor
public class WechatLoginStrategy implements LoginStrategy {

    private final AuthClient authClient;
    private final LoginTokenService loginTokenService;

    @Override
    public boolean support(IdentityTypeEnum identityType, CredentialTypeEnum credentialType) {
        return identityType == IdentityTypeEnum.WECHAT && credentialType == CredentialTypeEnum.OAUTH_TOKEN;
    }

    @Override
    public OAuth2TokenResp login(LoginReq request) {
        String code = request.getCredential();
        if (!StringUtils.hasText(code)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "微信授权 code 不能为空");
        }
        WechatLoginReqDTO wxReq = new WechatLoginReqDTO();
        wxReq.setCode(code);
        LoginRespDTO userResp = authClient.loginByWechat(wxReq);
        return loginTokenService.createLoginResponse(userResp);
    }
}
