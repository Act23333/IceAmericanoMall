package org.icedamericanomall.user.adapter.internal;

import lombok.RequiredArgsConstructor;
import org.icedamericanomall.dto.*;
import org.icedamericanomall.user.service.AuthService;
import org.icedamericanomall.common.annotation.RateLimit;
import org.springframework.web.bind.annotation.*;

/**
 * 认证域内部接口 —— Feign 调用（原本混在 InternalUserController 中）。
 */
@RestController
@RequestMapping("/internal/auth")
@RequiredArgsConstructor
public class InternalAuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @RateLimit(key = "#registerReqDTO.deviceId", limit = 100, duration = 3600)
    public LoginRespCO register(@RequestBody RegisterReqCO registerReqDTO) {
        return authService.register(registerReqDTO);
    }

    @PostMapping("/web/password/login")
    public LoginRespCO loginByPassword(@RequestBody PasswordLoginReqCO passwordLoginReqDTO) {
        return authService.loginByPassword(passwordLoginReqDTO);
    }

    @PostMapping("/web/sms/login")
    public LoginRespCO loginBySms(@RequestBody SmsLoginReqCO smsLoginReqDTO) {
        return authService.loginBySms(smsLoginReqDTO);
    }

    @PostMapping("/web/wechat/login")
    public LoginRespCO loginByWechat(@RequestBody WechatWebLoginReqCO wechatWebLoginReqDTO) {
        return authService.loginByWechat(wechatWebLoginReqDTO);
    }

    @PostMapping("/wechat/mini/login")
    public LoginRespCO wechatMiniLogin(@RequestBody WechatMiniLoginReqCO wechatMiniLoginReqDTO) {
        return authService.wechatMiniLogin(wechatMiniLoginReqDTO);
    }

    @PostMapping("/reset-password")
    public void resetPassword(@RequestBody org.icedamericanomall.dto.ResetPasswordReqCO request) {
        authService.resetPassword(request);
    }
}
