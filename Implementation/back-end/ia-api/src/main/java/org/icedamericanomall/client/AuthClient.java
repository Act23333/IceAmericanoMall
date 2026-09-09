package org.icedamericanomall.client;

import org.icedamericanomall.dto.*;
import org.icedamericanomall.fallback.AuthClientFallback;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 认证域 Feign Client —— 从 UserClient 拆分出的 register/login/reset-password。
 */
@FeignClient(name = "user-service", path = "/internal/auth", contextId = "auth",
        fallbackFactory = AuthClientFallback.class)
public interface AuthClient {

    @PostMapping("/register")
    LoginRespDTO register(@RequestBody RegisterReqDTO request);

    @PostMapping("/login/password")
    LoginRespDTO loginByPassword(@RequestBody PasswordLoginReqDTO request);

    @PostMapping("/login/sms")
    LoginRespDTO loginBySms(@RequestBody SmsLoginReqDTO request);

    @PostMapping("/login/wechat")
    LoginRespDTO loginByWechat(@RequestBody WechatLoginReqDTO request);

    @PostMapping("/reset-password")
    void resetPassword(@RequestBody ResetPasswordReqDTO request);
}
