package org.icedamericanomall.auth.domain.port;

import org.icedamericanomall.auth.domain.model.WechatSession;

/**
 * 微信网页（OAuth2）会话换取能力（领域层定义，基础设施层直连微信开放平台）。
 *
 * <p>V5.2（ADR-017 认证边界收敛）：网页微信登录不再委托 user-service 的
 * {@code /internal/auth/web/wechat/login}（认证内部契约整体废弃，见 ADR-017），改为
 * 本上下文自持闭环 —— {@code code → openid/nickname} 由第三方能力完成，
 * 凭证建档由 {@link org.icedamericanomall.auth.domain.service.UserAuthProvisioner} 承担，
 * 与小程序策略（{@link WechatMiniSessionService}）完全对称。
 *
 * <p>隔离第三方模型：领域层只认 {@link WechatSession}，不认微信 SDK 的响应体。
 */
public interface WechatWebSessionService {

    /**
     * 用网页授权 code 换取微信会话标识。
     *
     * @param code  前端 OAuth 授权回调拿到的 code
     * @param state 防 CSRF 的 state（透传审计）
     * @return 微信会话标识（openid / unionId / nickname，部分可为空）
     */
    WechatSession exchange(String code, String state);
}
