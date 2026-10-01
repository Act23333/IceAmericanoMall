package org.icedamericanomall.user.domain.gateway;

import org.icedamericanomall.user.domain.model.WechatProfile;

/**
 * 微信 OAuth 网关（防腐层：调用 ia-integration 的 {@code WechatOAuthThirdParty}）。
 *
 * <p>领域只表达"用授权 code 换取微信用户资料"这一能力；
 * 外部模型（{@code WechatOAuthResp}）→ 领域值对象（{@link WechatProfile}）
 * 的转换由基础设施层实现负责。真实 / Mock 客户端由 {@code wechat.oauth.enabled} 决定。
 */
public interface WechatOAuthGateway {

    /**
     * 用微信授权 code 换取用户资料。
     *
     * @param code 前端 OAuth 授权回调 code
     * @return 微信用户资料（openid 必有）
     */
    WechatProfile getUserInfoByCode(String code);
}
