package org.icedAmericanoMall.client;

import org.icedAmericanoMall.dto.*;
import org.icedAmericanoMall.fallback.UserClientFallback;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "user-service", path = "/internal/user", contextId = "internal", fallbackFactory = UserClientFallback.class)
public interface UserClient {

    @PostMapping("/register")
    LoginRespDTO register(@RequestBody RegisterReqDTO request);

    @PostMapping("/login/password")
    LoginRespDTO loginByPassword(@RequestBody PasswordLoginReqDTO request);

    @PostMapping("/login/sms")
    LoginRespDTO loginBySms(@RequestBody SmsLoginReqDTO request);

    /** Get total user count for admin dashboard */
    @GetMapping("/count")
    Long countUsers();

    /** Award points — called by trade-service on order completion */
    @PostMapping("/points/add")
    Long addPoints(@RequestParam Long userId, @RequestParam int points,
                   @RequestParam(defaultValue = "2") int type,
                   @RequestParam(defaultValue = "下单奖励") String source);
}
