package org.icedamericanomall.auth.domain.gateway;

import org.icedamericanomall.auth.domain.model.UserIdentity;

/**
 * 微信网页（OAuth2）登录网关（防腐层：调用 user-service）。
 *
 * <p>网页扫码登录的后续动作（code 换 openid、自动注册、注册即登录奖励、
 * 冻结校验）由 user-service 的用户上下文负责，本上下文只负责把结果
 * 转换成本上下文的 {@link UserIdentity}。
 */
public interface WechatWebLoginGateway {

    /**
     * 委托 user-service 完成网页微信登录。
     *
     * @param code  微信授权回调 code
     * @param state 防 CSRF 的 state
     * @return 用户身份（userId / username）
     */
    UserIdentity loginByWebCode(String code, String state);
}
