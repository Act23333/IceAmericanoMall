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
            @Override public LoginRespCO register(RegisterReqCO r) { log.error("注册失败", cause); return null; }
            @Override public LoginRespCO loginByPassword(PasswordLoginReqCO r) { log.error("登录失败", cause); return null; }
            @Override public LoginRespCO loginBySms(SmsLoginReqCO r) { log.error("登录失败", cause); return null; }
            @Override public LoginRespCO loginByWechat(WechatWebLoginReqCO r) { log.error("微信网页登录失败", cause); return null; }
            @Override public LoginRespCO wechatMiniLogin(WechatMiniLoginReqCO r) { log.error("微信小程序登录失败", cause); return null; }
            @Override public void resetPassword(ResetPasswordReqCO r) { log.error("密码重置失败", cause); }
        };
    }
}
