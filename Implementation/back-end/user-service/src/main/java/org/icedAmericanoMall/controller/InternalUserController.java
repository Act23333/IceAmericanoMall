package org.icedAmericanoMall.controller;

import lombok.RequiredArgsConstructor;
import org.icedAmericanoMall.domain.vo.AddressResp;
import org.icedAmericanoMall.domain.vo.UserInfoResp;
import org.icedAmericanoMall.dto.LoginRespDTO;
import org.icedAmericanoMall.dto.PasswordLoginReqDTO;
import org.icedAmericanoMall.dto.RegisterReqDTO;
import org.icedAmericanoMall.dto.SmsLoginReqDTO;
import org.icedAmericanoMall.service.AddressService;
import org.icedAmericanoMall.service.AuthService;
import org.icedAmericanoMall.service.PointsService;
import org.icedAmericanoMall.service.UserService;
import org.noLazy.common.annotation.RateLimit;
import org.noLazy.common.utils.RateLimitUtils;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/user")
public class InternalUserController {

    private final AuthService authService;
    private final UserService userService;
    private final AddressService addressService;
    private final PointsService pointsService;
    private final RateLimitUtils rateLimitUtils;

    public InternalUserController(AuthService authService, UserService userService,
                                   AddressService addressService, PointsService pointsService,
                                   RateLimitUtils rateLimitUtils) {
        this.authService = authService;
        this.userService = userService;
        this.addressService = addressService;
        this.pointsService = pointsService;
        this.rateLimitUtils = rateLimitUtils;
    }

    @PostMapping("/register")
    @RateLimit(key = "#registerReqDTO.deviceId", limit = 3, duration = 3600)
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

    /**
     * 微信 OAuth 登录 — 内部 Feign 调用。
     */
    @PostMapping("/login/wechat")
    public LoginRespDTO loginByWechat(@RequestBody org.icedAmericanoMall.dto.WechatLoginReqDTO request) {
        return authService.loginByWechat(request);
    }

    /**
     * 找回密码：短信验证码重置密码 — 内部 Feign 调用。
     */
    @PostMapping("/reset-password")
    public void resetPassword(@RequestBody org.icedAmericanoMall.dto.ResetPasswordReqDTO request) {
        authService.resetPassword(request);
    }

    /**
     * Get address by ID for order address snapshot — internal Feign use.
     */
    @GetMapping("/address/{id}")
    public AddressResp getAddress(@PathVariable Long id) {
        return addressService.getAddressById(id);
    }

    /**
     * Get basic user info by ID — internal Feign use.
     */
    @GetMapping("/{id}")
    public UserInfoResp getUser(@PathVariable Long id) {
        return userService.getByUserId(id);
    }

    /**
     * Get total user count — internal Feign use (admin dashboard).
     */
    @GetMapping("/count")
    public long countUsers() {
        return userService.countUsers();
    }

    /**
     * Award points — internal Feign use (trade-service calls on order completion).
     */
    @PostMapping("/points/add")
    public long addPoints(@RequestParam Long userId, @RequestParam int points,
                           @RequestParam(defaultValue = "2") int type,
                           @RequestParam(defaultValue = "下单奖励") String source) {
        return pointsService.addPoints(userId, points, type, source);
    }
}