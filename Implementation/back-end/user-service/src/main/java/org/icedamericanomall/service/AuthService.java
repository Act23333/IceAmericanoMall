package org.icedamericanomall.service;

import org.icedamericanomall.dto.LoginRespDTO;
import org.icedamericanomall.dto.PasswordLoginReqDTO;
import org.icedamericanomall.dto.RegisterReqDTO;
import org.icedamericanomall.dto.ResetPasswordReqDTO;
import org.icedamericanomall.dto.SmsLoginReqDTO;
import org.icedamericanomall.dto.WechatLoginReqDTO;

public interface AuthService {
    LoginRespDTO register(RegisterReqDTO request);
    LoginRespDTO loginByPassword(PasswordLoginReqDTO request);
    LoginRespDTO loginBySms(SmsLoginReqDTO request);

    /** 微信 OAuth 登录：code→openid，按 openid 查/建用户。 */
    LoginRespDTO loginByWechat(WechatLoginReqDTO request);

    /** 找回密码：校验短信验证码后重置密码。 */
    void resetPassword(ResetPasswordReqDTO request);
}
