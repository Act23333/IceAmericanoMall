package org.icedamericanomall.auth.app.dto;

import lombok.Builder;
import org.icedamericanomall.auth.domain.model.userauth.UserAuth;

/**
 * 登录结果（应用层出参，供适配层转换为 VO）。
 *
 * <p>只承载对外契约需要的字段：Token 三元组 + userId。
 *
 * @param userId       用户 ID
 * @param accessToken  访问令牌
 * @param refreshToken 刷新令牌
 * @param expiresIn    accessToken 有效期（秒）
 */
@Builder
public record LoginDTO(
        Long userId,
        String accessToken,
        String refreshToken,
        Long expiresIn
) {

    /** 由认证凭证聚合构造 */
    public static LoginDTO of(UserAuth userAuth, TokenPair tokenPair) {
        return LoginDTO.builder()
                .userId(userAuth.getUserId())
                .accessToken(tokenPair.accessToken())
                .refreshToken(tokenPair.refreshToken())
                .expiresIn(tokenPair.expiresIn())
                .build();
    }

    /** 由用户 ID + Token 对构造（注册等无聚合场景） */
    public static LoginDTO of(Long userId, TokenPair tokenPair) {
        return LoginDTO.builder()
                .userId(userId)
                .accessToken(tokenPair.accessToken())
                .refreshToken(tokenPair.refreshToken())
                .expiresIn(tokenPair.expiresIn())
                .build();
    }
}
