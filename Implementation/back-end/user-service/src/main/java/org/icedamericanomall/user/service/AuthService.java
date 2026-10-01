package org.icedamericanomall.user.service;

import org.icedamericanomall.dto.*;

public interface AuthService {
    LoginRespCO register(RegisterReqCO request);
    LoginRespCO loginByPassword(PasswordLoginReqCO request);
    LoginRespCO loginBySms(SmsLoginReqCO request);

    /** 微信 OAuth 登录：code→openid，按 openid 查/建用户。 */
    LoginRespCO loginByWechat(WechatWebLoginReqCO request);

    /** 微信小程序登录 */
    LoginRespCO wechatMiniLogin(WechatMiniLoginReqCO wechatMiniLoginReqDTO);

    /** 找回密码：校验短信验证码后重置密码。 */
    void resetPassword(ResetPasswordReqCO request);
}
