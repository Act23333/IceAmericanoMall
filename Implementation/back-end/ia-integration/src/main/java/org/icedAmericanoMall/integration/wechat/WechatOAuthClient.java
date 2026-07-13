package org.icedAmericanoMall.integration.wechat;

/**
 * 微信 OAuth 客户端 —— 用授权 code 换取用户信息（含 openid）。
 * 真实实现（{@link RealWechatOAuthClient}）在配置了 {@code wechat.oauth.app-id} 时激活；
 * 否则由 {@link MockWechatOAuthClient} 返回虚拟数据（本地/未接入微信开放平台时）。
 */
public interface WechatOAuthClient {

    /**
     * 用微信授权 code 换取用户信息。
     * @param code 前端 OAuth 授权回调拿到的 code
     * @return 微信用户信息（openid 必有，昵称/头像可能为空）
     */
    WechatUserInfo getUserInfoByCode(String code);
}
