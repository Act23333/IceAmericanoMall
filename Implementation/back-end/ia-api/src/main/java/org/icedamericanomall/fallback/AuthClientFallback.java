package org.icedamericanomall.fallback;

import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.client.AuthClient;
import org.icedamericanomall.dto.*;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AuthClientFallback implements FallbackFactory<AuthClient> {
    @Override
    public AuthClient create(Throwable cause) {
        return new AuthClient() {
            @Override public LoginRespDTO register(RegisterReqDTO r) { log.error("注册失败", cause); return null; }
            @Override public LoginRespDTO loginByPassword(PasswordLoginReqDTO r) { log.error("登录失败", cause); return null; }
            @Override public LoginRespDTO loginBySms(SmsLoginReqDTO r) { log.error("登录失败", cause); return null; }
            @Override public LoginRespDTO loginByWechat(WechatLoginReqDTO r) { log.error("微信登录失败", cause); return null; }
            @Override public void resetPassword(ResetPasswordReqDTO r) { log.error("密码重置失败", cause); }
        };
    }
}
