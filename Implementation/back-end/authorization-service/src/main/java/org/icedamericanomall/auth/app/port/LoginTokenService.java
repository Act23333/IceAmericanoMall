package org.icedamericanomall.auth.app.port;

import org.icedamericanomall.auth.app.dto.SessionCheckDTO;
import org.icedamericanomall.auth.app.dto.TokenGenerateParam;
import org.icedamericanomall.auth.app.dto.TokenPair;

/**
 * 登录令牌能力（应用层端口，基础设施层用 JWT + Redis 实现）。
 */
public interface LoginTokenService {

    /**
     * 创建登录会话：签发 accessToken + refreshToken，并写入 Redis。
     *
     * @param tokenGenerateParam 用户 ID + 平台 + 设备
     * @return Token 对（accessToken / refreshToken / 有效期）
     */
    TokenPair createLoginResponse(TokenGenerateParam tokenGenerateParam);

    /**
     * 刷新会话：校验 refreshToken 并轮换签发新的 Token 对。
     *
     * @param refreshToken 刷新令牌
     * @return 新的 Token 对
     */
    TokenPair createRefreshResponse(String refreshToken);

    /**
     * 检查会话状态：验签 + 踢出校验，返回剩余 TTL。
     *
     * @param accessToken 访问令牌
     * @return 会话状态
     */
    SessionCheckDTO checkSession(String accessToken);

    /**
     * 登出：吊销 refreshToken，使 accessToken 因 jti 不匹配而失效。
     *
     * @param refreshToken 刷新令牌
     */
    void logout(String refreshToken);
}
