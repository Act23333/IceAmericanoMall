package org.icedAmericanoMall.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedAmericanoMall.client.UserClient;
import org.icedAmericanoMall.domain.dto.OAuth2TokenResp;
import org.icedAmericanoMall.domain.dto.RefreshTokenInfo;
import org.icedAmericanoMall.domain.dto.RegisterReq;
import org.icedAmericanoMall.domain.dto.auth.LoginReq;
import org.icedAmericanoMall.dto.LoginRespDTO;
import org.icedAmericanoMall.dto.RegisterReqDTO;
import org.icedAmericanoMall.service.auth.login.LoginContext;
import org.icedAmericanoMall.service.auth.login.LoginTokenService;
import org.icedAmericanoMall.utils.RefreshTokenUtils;
import org.noLazy.common.domain.Result;
import org.noLazy.common.utils.BeanUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserClient userClient;
    private final LoginContext loginContext;
    private final LoginTokenService loginTokenService;
    private final RefreshTokenUtils refreshTokenUtils;

    /**
     * 用户登录接口。
     * <p>
     * 根据登录类型（密码登录、短信验证码登录等）自动匹配策略，完成认证后返回双 Token（access_token + refresh_token）。
     *
     * @param request 登录请求体，包含账号标识、凭证及登录类型
     * @return 包含 {@link OAuth2TokenResp} 的 {@link Result}，其中 accessToken 用于接口访问，refreshToken 用于续期
     * @throws  org.noLazy.common.exception.BizException 账号不存在、密码错误或账号被禁用时抛出系统内部异常
     */
    @PostMapping("/login")
    public Result<OAuth2TokenResp> login(@Validated @RequestBody LoginReq request) {
        return Result.ok(loginContext.login(request));
    }

    /**
     *
     * @param request
     * @return
     */
    @PostMapping("/register")
    public Result<OAuth2TokenResp> register(@Validated @RequestBody RegisterReq request) {
        RegisterReqDTO registerReqDTO = BeanUtils.copyBean(request, RegisterReqDTO.class);
        LoginRespDTO userResp = userClient.register(registerReqDTO);
        return Result.ok(loginTokenService.createLoginResponse(userResp));
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
}