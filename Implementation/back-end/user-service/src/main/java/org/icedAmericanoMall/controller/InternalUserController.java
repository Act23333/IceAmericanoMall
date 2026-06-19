package org.icedAmericanoMall.controller;

import lombok.RequiredArgsConstructor;
import org.icedAmericanoMall.domain.vo.AddressResp;
import org.icedAmericanoMall.domain.vo.UserInfoResp;
import org.icedAmericanoMall.dto.LoginRespDTO;
import org.icedAmericanoMall.dto.PasswordLoginReqDTO;
import org.icedAmericanoMall.dto.RegisterReqDTO;
import org.icedAmericanoMall.dto.SmsLoginReqDTO;
import org.icedAmericanoMall.service.AddressService;
import org.icedAmericanoMall.service.PointsService;
import org.icedAmericanoMall.service.UserService;
import org.noLazy.common.annotation.RateLimit;
import org.noLazy.common.utils.RateLimitUtils;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/user")
@RequiredArgsConstructor
public class InternalUserController {

    private final UserService userService;
    private final AddressService addressService;
    private final PointsService pointsService;
    private final RateLimitUtils rateLimitUtils;

    @PostMapping("/register")
    @RateLimit(key = "#registerReqDTO.deviceId", limit = 3, duration = 3600)
    public LoginRespDTO register(@RequestBody RegisterReqDTO registerReqDTO) {
        return userService.register(registerReqDTO);
    }

    @PostMapping("/login/password")
    public LoginRespDTO loginByPassword(@RequestBody PasswordLoginReqDTO passwordLoginReqDTO) {
        return userService.loginByPassword(passwordLoginReqDTO);
    }

    @PostMapping("/login/sms")
    public LoginRespDTO loginBySms(@RequestBody SmsLoginReqDTO smsLoginReqDTO) {
        return userService.loginBySms(smsLoginReqDTO);
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