// 路径：org.nolazy.userservice.controller.InternalUserController
package org.icedAmericanoMall.controller;

import lombok.RequiredArgsConstructor;
import org.icedAmericanoMall.service.UserService;
import org.icedAmericanoMall.dto.LoginRespDTO;
import org.icedAmericanoMall.dto.PasswordLoginReqDTO;
import org.icedAmericanoMall.dto.RegisterReqDTO;
import org.icedAmericanoMall.dto.SmsLoginReqDTO;
import org.noLazy.common.annotation.RateLimit;
import org.noLazy.common.utils.RateLimitUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/user")
@RequiredArgsConstructor
public class InternalUserController {

    private final UserService userService;
    private final RateLimitUtils rateLimitUtils;
    @PostMapping("/register")
    @RateLimit(key = "#registerReqDTO.deviceId", limit = 3, duration = 3600)
    public LoginRespDTO register(@RequestBody RegisterReqDTO registerReqDTO) {
        return userService.register(registerReqDTO);
    }


    //TODO: 如果登录次数超过限流额度，则验证码验证码，用户重新发送
    //密码失败次数限流，需要配合失败计数，所以不使用 @RateLimit
//    @RateLimit(key = "@RateLimit(key = #request.phone ?: #request.username)")
    //@RateLimit(key = "#request.getParameter('phone') ?: #request.getParameter('username')")
    @PostMapping("/login/password")
    public LoginRespDTO loginByPassword(@RequestBody PasswordLoginReqDTO passwordLoginReqDTO) {
        return userService.loginByPassword(passwordLoginReqDTO);
    }
    @PostMapping("/login/sms")
    public LoginRespDTO loginBySms(@RequestBody SmsLoginReqDTO smsLoginReqDTO) {
        return userService.loginBySms(smsLoginReqDTO);
    }
}