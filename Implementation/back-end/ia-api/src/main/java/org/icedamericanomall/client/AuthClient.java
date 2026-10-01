package org.icedamericanomall.client;

import org.icedamericanomall.dto.*;
import org.icedamericanomall.fallback.AuthClientFallback;
import org.icedamericanomall.common.model.EncryptedIdentifier;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 认证域 Feign Client —— 从 UserClient 拆分出的 register/login/reset-password。
 *
 * <p><b>路径以服务端（user-service {@code InternalAuthController}）为准</b>：
 * 曾出现客户端 {@code /web/sms/login} vs 服务端 {@code /login/sms/login}、
 * 客户端 {@code /wechat/web/login} vs 服务端 {@code /web/wechat/login} 的错配，
 * 导致运行期 404。修改任一端路径时必须同步另一端。
 */
@FeignClient(name = "user-service", path = "/internal/auth", contextId = "auth",
        fallbackFactory = AuthClientFallback.class)
public interface AuthClient {

    @PostMapping("/register")
    LoginRespCO register(@RequestBody RegisterReqCO request);

    @PostMapping("/web/password/login")
    LoginRespCO loginByPassword(@RequestBody PasswordLoginReqCO request);

    @PostMapping("/web/sms/login")
    LoginRespCO loginBySms(@RequestBody SmsLoginReqCO request);

    @PostMapping("/web/wechat/login")
    LoginRespCO loginByWechat(@RequestBody WechatWebLoginReqCO request);

    @PostMapping("/wechat/mini/login")
    LoginRespCO wechatMiniLogin(@RequestBody WechatMiniLoginReqCO wechatMiniLoginReqDTO);

    @PostMapping("/reset-password")
    void resetPassword(@RequestBody ResetPasswordReqCO request);


}
