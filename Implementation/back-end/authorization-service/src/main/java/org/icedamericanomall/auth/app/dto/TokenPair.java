package org.icedamericanomall.auth.app.dto;

/**
 * Token 对（应用层出参）。
 *
 * @param userId       用户 ID
 * @param accessToken  访问令牌
 * @param refreshToken 刷新令牌
 * @param expiresIn    accessToken 有效期（秒）
 */
public record TokenPair(
        Long userId,
        String accessToken,
        String refreshToken,
        long expiresIn
) {
}
