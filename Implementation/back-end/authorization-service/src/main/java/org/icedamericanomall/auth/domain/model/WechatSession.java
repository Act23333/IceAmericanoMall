package org.icedamericanomall.auth.domain.model;

/**
 * 微信授权换取到的会话标识（第三方模型在本上下文的领域表达）。
 *
 * @param openid   微信应用内唯一标识
 * @param unionId  微信开放平台跨应用唯一标识（可能为空）
 * @param phone    微信绑定的手机号（小程序手机号授权时才有值）
 * @param nickname 微信昵称（网页授权可得；小程序 jsCode2session 无昵称，为 null）
 */
public record WechatSession(
        String openid,
        String unionId,
        String phone,
        String nickname
) {

    /** 兼容构造：无昵称口径（小程序换取）。 */
    public WechatSession(String openid, String unionId, String phone) {
        this(openid, unionId, phone, null);
    }
}
