package org.icedamericanomall.auth.domain.model;

/**
 * 微信授权换取到的会话标识（第三方模型在本上下文的领域表达）。
 *
 * @param openid  微信应用内唯一标识
 * @param unionId 微信开放平台跨应用唯一标识（可能为空）
 * @param phone   微信绑定的手机号（小程序手机号授权时才有值）
 */
public record WechatSession(
        String openid,
        String unionId,
        String phone
) {
}
