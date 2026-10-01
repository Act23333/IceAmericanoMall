package org.icedamericanomall.user.infrastructure.client;

import lombok.RequiredArgsConstructor;
import org.icedamericanomall.integration.auth.model.WechatOAuthResp;
import org.icedamericanomall.integration.auth.service.thirdparty.WechatOAuthThirdParty;
import org.icedamericanomall.user.domain.gateway.WechatOAuthGateway;
import org.icedamericanomall.user.domain.model.WechatProfile;
import org.springframework.stereotype.Component;

/**
 * 微信 OAuth 网关实现（基础设施层，防腐层）—— 调用 ia-integration 的
 * {@code WechatOAuthThirdParty}（真实 / Mock 由 {@code wechat.oauth.enabled} 决定），
 * 把外部模型 {@code WechatOAuthResp} 转换为领域值对象 {@link WechatProfile}。
 */
@Component
@RequiredArgsConstructor
public class WechatOAuthGatewayImpl implements WechatOAuthGateway {

    private final WechatOAuthThirdParty wechatOAuthThirdParty;

    @Override
    public WechatProfile getUserInfoByCode(String code) {
        WechatOAuthResp resp = wechatOAuthThirdParty.getUserInfoByCode(code);
        if (resp == null) {
            return new WechatProfile(null, null, null);
        }
        return new WechatProfile(resp.getOpenid(), resp.getNickname(), resp.getAvatarUrl());
    }
}
