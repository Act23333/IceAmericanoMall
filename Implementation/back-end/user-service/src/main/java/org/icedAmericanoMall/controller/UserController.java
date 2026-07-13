package org.icedAmericanoMall.controller;

import lombok.extern.slf4j.Slf4j;
import org.icedAmericanoMall.domain.dto.UpdateProfileReq;
import org.icedAmericanoMall.service.SmsService;
import org.icedAmericanoMall.service.UserService;
import org.icedAmericanoMall.domain.dto.SmsCodeSendReq;
import org.icedAmericanoMall.domain.vo.UserInfoResp;
import org.noLazy.common.annotation.RateLimit;
import org.noLazy.common.domain.Result;
import org.noLazy.common.enums.ErrorCode;
import org.noLazy.common.exception.BizException;
import org.noLazy.common.utils.RateLimitUtils;
import org.noLazy.common.utils.UserContext;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.TimeUnit;

/**
 * @ClassName: UserController
 * @Description: TODO
 * @Author: noLazy
 * @Date: 2026/3/17 10:10
 * @Version: 1.0.0
 * @ProjectName: IcedAmericanoMall
 * @Package: org.icedAmericanoMall.controller
 */

@RequestMapping("/api/user")
@RestController
@Slf4j
public class UserController {
    private final UserService userService;
    private final SmsService smsService;
    private final RateLimitUtils rateLimitUtils;

    public UserController(UserService userService, SmsService smsService, RateLimitUtils rateLimitUtils) {
        this.userService = userService;
        this.smsService = smsService;
        this.rateLimitUtils = rateLimitUtils;
    }

    @PostMapping("/code")
    @RateLimit(key = "ip", limit = 10, duration = 1, unit = TimeUnit.MINUTES)
    public Result<Void> sendCode(@RequestBody SmsCodeSendReq smsCodeSendReq) {
        if (!rateLimitUtils.checkSmsByPhone(smsCodeSendReq.getPhone())) {
            throw new BizException(ErrorCode.FREQUENT_ERROR, "短信发送过于频繁，请稍后再试");
        }
        smsService.sendCode(smsCodeSendReq);
        return Result.ok("验证码已发送");
    }

    @GetMapping("/info")
    public Result<UserInfoResp> getUserInfo() {
        Long userId = UserContext.getUser();
        UserInfoResp userInfoResp = userService.getByUserId(userId);
        return Result.ok(userInfoResp);
    }

    @PutMapping("/profile")
    public Result<Void> updateProfile(@RequestBody UpdateProfileReq req) {
        Long userId = UserContext.getUser();
        userService.updateProfile(userId, req);
        return Result.ok();
    }
}