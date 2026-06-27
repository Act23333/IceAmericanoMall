package org.icedAmericanoMall.service.auth.login;

import lombok.RequiredArgsConstructor;
import org.icedAmericanoMall.domain.dto.OAuth2TokenResp;
import org.icedAmericanoMall.domain.dto.RefreshTokenInfo;
import org.icedAmericanoMall.dto.LoginRespDTO;
import org.icedAmericanoMall.utils.JwtUtils;
import org.icedAmericanoMall.utils.RefreshTokenUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class LoginTokenService {

    private final JwtUtils jwtService;
    private final RefreshTokenUtils refreshTokenUtils;

    @Value("${jwt.access-token-ttl:3600}")
    private Long accessTokenTtl;

    @Value("${jwt.refresh-token-ttl:604800}")
    private Long refreshTokenTtl;

    public OAuth2TokenResp createLoginResponse(LoginRespDTO userResp) {
        Long userId = userResp.getUserId();
        String username = userResp.getUsername();
        String accessToken = jwtService.generateToken(buildClaims(userResp));
        String refreshToken = refreshTokenUtils.createRefreshToken(userId, username, refreshTokenTtl);
        return buildTokenResponse(accessToken, userId, username, refreshToken);
    }

    public OAuth2TokenResp createRefreshResponse(RefreshTokenInfo info, String refreshToken) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", info.getUserId());
        claims.put("username", info.getUsername());
        String accessToken = jwtService.generateToken(claims);
        return buildTokenResponse(accessToken, info.getUserId(), info.getUsername(), refreshToken);
    }

    private Map<String, Object> buildClaims(LoginRespDTO userResp) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userResp.getUserId());
        claims.put("username", userResp.getUsername());
        claims.put("role", userResp.getRole() != null ? userResp.getRole() : "ROLE_USER");
        return claims;
    }

    private OAuth2TokenResp buildTokenResponse(String accessToken, Long userId, String username, String refreshToken) {
        OAuth2TokenResp response = new OAuth2TokenResp();
        response.setAccessToken(accessToken);
        response.setRefreshToken(refreshToken);
        response.setExpiresIn(accessTokenTtl);
        response.setUserId(userId);
        response.setUsername(username);
        return response;
    }
}
