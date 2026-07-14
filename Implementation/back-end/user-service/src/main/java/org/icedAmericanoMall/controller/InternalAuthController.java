package org.icedAmericanoMall.controller;

import lombok.RequiredArgsConstructor;
import org.icedAmericanoMall.dto.LoginRespDTO;
import org.icedAmericanoMall.dto.PasswordLoginReqDTO;
import org.icedAmericanoMall.dto.RegisterReqDTO;
import org.icedAmericanoMall.dto.SmsLoginReqDTO;
import org.icedAmericanoMall.service.AuthService;
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
    public LoginRespDTO register(@RequestBody RegisterReqDTO registerReqDTO) {
        return authService.register(registerReqDTO);
    }

    @PostMapping("/login/password")
    public LoginRespDTO loginByPassword(@RequestBody PasswordLoginReqDTO passwordLoginReqDTO) {
        return authService.loginByPassword(passwordLoginReqDTO);
    }

    @PostMapping("/login/sms")
    public LoginRespDTO loginBySms(@RequestBody SmsLoginReqDTO smsLoginReqDTO) {
        return authService.loginBySms(smsLoginReqDTO);
    }

    @PostMapping("/login/wechat")
    public LoginRespDTO loginByWechat(@RequestBody org.icedAmericanoMall.dto.WechatLoginReqDTO request) {
        return authService.loginByWechat(request);
    }

    @PostMapping("/reset-password")
    public void resetPassword(@RequestBody org.icedAmericanoMall.dto.ResetPasswordReqDTO request) {
        authService.resetPassword(request);
    }
}
