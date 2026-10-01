package org.icedamericanomall.auth.domain.ability.auth.impl;

import lombok.RequiredArgsConstructor;
import org.icedamericanomall.auth.domain.ability.auth.LoginStrategy;
import org.icedamericanomall.auth.domain.enums.CredentialTypeEnum;
import org.icedamericanomall.auth.domain.gateway.WechatWebLoginGateway;
import org.icedamericanomall.auth.domain.model.LoginSpec;
import org.icedamericanomall.auth.domain.model.UserAuth;
import org.icedamericanomall.auth.domain.model.UserIdentity;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.enums.IdentityTypeEnum;
import org.icedamericanomall.common.exception.BizException;

/**
 * 微信网页（OAuth2）登录策略（领域能力）。
 *
 * <p>WECHAT + WECHAT_WEB_CODE：{@code credential} 承载前端授权回调拿到的 code。
 * code→openid、自动注册、注册即登录奖励、冻结校验等由 user-service 的用户上下文
 * 负责（见 {@link WechatWebLoginGateway} 防腐层），本策略只把返回的身份
 * 装配成本上下文的认证凭证聚合。
 */
@RequiredArgsConstructor
public class WechatWebLoginStrategy implements LoginStrategy {

    private final WechatWebLoginGateway wechatWebLoginGateway;

    @Override
    public boolean support(IdentityTypeEnum identityType, CredentialTypeEnum credentialType) {
        return identityType == IdentityTypeEnum.WECHAT
                && credentialType == CredentialTypeEnum.WECHAT_WEB_CODE;
    }

    @Override
    public UserAuth login(LoginSpec spec) {
        String code = spec.credential();
        if (code == null || code.isBlank()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "微信授权 code 不能为空");
        }
        UserIdentity identity = wechatWebLoginGateway.loginByWebCode(code, spec.extra("state"));
        if (identity == null || identity.userId() == null) {
            throw new BizException(ErrorCode.WECHAT_AUTH_FAILED, "微信授权失败");
        }
        return UserAuth.builder()
                .userId(identity.userId())
                .identityType(IdentityTypeEnum.WECHAT)
                .build();
    }
}
