package org.icedamericanomall.auth.domain.port;

import org.icedamericanomall.auth.domain.model.WechatSession;

/**
 * 微信小程序登录会话换取能力（领域层定义，基础设施层对接微信开放平台）。
 *
 * <p>隔离第三方模型：领域层只认 {@link WechatSession}，不认微信 SDK 的响应体。
 */
public interface WechatMiniSessionService {

    /**
     * 用小程序 {@code wx.login()} 的临时凭证换取会话标识。
     *
     * @param code      临时登录凭证
     * @param phoneCode 手机号授权凭证（可为空）
     * @return 微信会话标识（openid / unionId / phone）
     */
    WechatSession exchange(String code, String phoneCode);
}
