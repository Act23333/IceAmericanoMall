package org.icedamericanomall.integration.auth.service.thirdparty;

import org.icedamericanomall.integration.auth.model.WechatOAuthResp;
import org.icedamericanomall.integration.auth.service.thirdparty.impl.MockWechatOAuthClient;
import org.icedamericanomall.integration.auth.service.thirdparty.impl.RealWechatOAuthClient;


/**
 * 微信 OAuth 客户端 —— 用授权 code 换取用户信息（含 openid）。
 * 真实实现（{@link RealWechatOAuthClient}）在配置了 {@code wechat.oauth.enabled} 时激活；
 * 否则由 {@link MockWechatOAuthClient} 返回虚拟数据（本地/未接入微信开放平台时）。
 */
public interface WechatOAuthThirdParty {

    /**
     * 用微信授权 code 换取用户信息。
     * @param code 前端 OAuth 授权回调拿到的 code
     * @return 微信用户信息（openid 必有，昵称/头像可能为空）
     */
    WechatOAuthResp getUserInfoByCode(String code);
}
