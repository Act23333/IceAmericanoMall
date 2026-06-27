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

    @PostMapping("/login")
    public Result<OAuth2TokenResp> login(@Validated @RequestBody LoginReq request) {
        return Result.ok(loginContext.login(request));
    }

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