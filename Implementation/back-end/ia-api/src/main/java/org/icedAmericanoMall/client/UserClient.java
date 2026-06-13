package org.icedAmericanoMall.client;

import org.icedAmericanoMall.dto.*;
import org.icedAmericanoMall.fallback.UserClientFallback;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "user-service", path = "/internal/user", fallbackFactory = UserClientFallback.class)
public interface UserClient {

    @PostMapping("/register")
    LoginRespDTO register(@RequestBody RegisterReqDTO request);

    @PostMapping("/login/password")
    LoginRespDTO loginByPassword(@RequestBody PasswordLoginReqDTO request);

    @PostMapping("/login/sms")
    LoginRespDTO loginBySms(@RequestBody SmsLoginReqDTO request);
}
