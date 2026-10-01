package org.icedamericanomall.user.domain.model;

/**
 * 微信用户资料（领域值对象）—— OAuth / 小程序 code 换取的结果。
 *
 * <p>防腐层的内部形态：外部模型（ia-integration 的 {@code WechatOAuthResp} 等）
 * 在基础设施层转换为本值对象后才进入领域层。
 *
 * @param openid    微信 openid（必有）
 * @param nickname  昵称（可为空）
 * @param avatarUrl 头像（可为空）
 */
public record WechatProfile(String openid, String nickname, String avatarUrl) {
}
