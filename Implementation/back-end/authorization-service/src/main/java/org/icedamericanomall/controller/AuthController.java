package org.icedamericanomall.controller;

import com.nimbusds.jwt.SignedJWT;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.client.AuthClient;
import org.icedamericanomall.client.UserClient;
import org.icedamericanomall.domain.dto.OAuth2TokenResp;
import org.icedamericanomall.domain.dto.RefreshTokenInfo;
import org.icedamericanomall.domain.dto.RegisterReq;
import org.icedamericanomall.domain.dto.auth.LoginReq;
import org.icedamericanomall.domain.enums.CredentialTypeEnum;
import org.icedamericanomall.domain.enums.IdentityTypeEnum;
import org.icedamericanomall.dto.LoginRespDTO;
import org.icedamericanomall.dto.RegisterReqDTO;
import org.icedamericanomall.dto.ResetPasswordReqDTO;
import org.icedamericanomall.dto.WechatLoginReqDTO;
import org.icedamericanomall.service.auth.login.LoginContext;
import org.icedamericanomall.service.auth.login.LoginTokenService;
import org.icedamericanomall.utils.JwtUtils;
import org.icedamericanomall.utils.RefreshTokenUtils;
import org.noLazy.common.domain.Result;
import org.noLazy.common.utils.BeanUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthClient authClient;
    private final UserClient userClient;
    private final LoginContext loginContext;
    private final LoginTokenService loginTokenService;
    private final RefreshTokenUtils refreshTokenUtils;
    private final JwtUtils jwtUtils;

    @PostMapping("/login")
    public Result<OAuth2TokenResp> login(@Validated @RequestBody LoginReq request) {
        return Result.ok(loginContext.login(request));
    }

    /** 微信 OAuth 登录：前端传授权 code，复用登录锁/失败计数与策略分发。 */
    @PostMapping("/login/wechat")
    public Result<OAuth2TokenResp> loginByWechat(@Validated @RequestBody WechatLoginReqDTO request) {
        LoginReq loginReq = new LoginReq();
        loginReq.setIdentityType(IdentityTypeEnum.WECHAT);
        loginReq.setCredentialType(CredentialTypeEnum.OAUTH_TOKEN);
        loginReq.setAccount(request.getCode());
        loginReq.setCredential(request.getCode());
        return Result.ok(loginContext.login(loginReq));
    }

    @PostMapping("/register")
    public Result<OAuth2TokenResp> register(@Validated @RequestBody RegisterReq request) {
        RegisterReqDTO registerReqDTO = BeanUtils.copyBean(request, RegisterReqDTO.class);
        LoginRespDTO userResp = authClient.register(registerReqDTO);
        return Result.ok(loginTokenService.createLoginResponse(userResp));
    }

    /** 找回密码：短信验证码重置密码（公开接口）。 */
    @PostMapping("/reset-password")
    public Result<Void> resetPassword(@Validated @RequestBody ResetPasswordReqDTO request) {
        authClient.resetPassword(request);
        return Result.ok("密码重置成功");
    }

    @PostMapping("/refresh")
    public Result<OAuth2TokenResp> refresh(@RequestParam("refresh_token") String refreshToken) {
        String newRefreshTokenId = refreshTokenUtils.rotateRefreshToken(refreshToken);
        RefreshTokenInfo info = refreshTokenUtils.getInfoByToken(newRefreshTokenId);
        return Result.ok(loginTokenService.createRefreshResponse(info, newRefreshTokenId));
    }

    @PostMapping("/logout")
    public Result<Void> logout(@RequestParam("refresh_token") String refreshToken) {
        refreshTokenUtils.revokeRefreshToken(refreshToken);
        return Result.ok("登出成功");
    }

    /**
     * 检查会话（空闲超时）— 验证 access_token 有效性，返回剩余 TTL。
     * 前端用来判断空闲超时（30 分钟无操作后 token 过期 → 需重新登录）。
     */
    @GetMapping("/check-session")
    public Result<Map<String, Object>> checkSession(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        long ttl = 0;
        boolean valid = false;
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            try {
                String token = authHeader.substring(7);
                SignedJWT jwt = jwtUtils.verifyToken(token);
                valid = true;
                Date exp = jwt.getJWTClaimsSet().getExpirationTime();
                ttl = Math.max(0, (exp.getTime() - System.currentTimeMillis()) / 1000);
            } catch (Exception ignored) { /* token 无效或过期 */ }
        }
        return Result.ok(Map.of("valid", valid, "ttlSeconds", ttl,
                "idleTimeoutMinutes", 30));
    }
}
