package org.icedamericanomall.manager;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.client.AuthClient;
import org.icedamericanomall.dto.LoginRespDTO;
import org.icedamericanomall.dto.PasswordLoginReqDTO;
import org.icedamericanomall.dto.RegisterReqDTO;
import org.icedamericanomall.dto.ResetPasswordReqDTO;
import org.icedamericanomall.dto.SmsLoginReqDTO;
import org.icedamericanomall.dto.WechatLoginReqDTO;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Component;

/**
 * V4.0 DDD: 认证编排 Manager（Application层 —— 职责: 用例编排+Feign调用+权限校验）。
 * <p>
 * Domain层(LoginStrategy)专注业务逻辑，Infrastructure层(AuthClient)专注远程调用。
 * Manager 负责将两者编排为一个完整用例。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthManager {

    private final AuthClient authClient;

    /** 密码登录: Manager编排 → Domain策略 + Feign调用 */
    public LoginRespDTO loginByPassword(PasswordLoginReqDTO req) {
        LoginRespDTO resp = authClient.loginByPassword(req);
        if (resp == null) {
            throw new org.noLazy.common.exception.BizException(
                    org.noLazy.common.enums.ErrorCode.PASSWORD_ERROR);
        }
        return resp;
    }

    /** 手机号登录: Domain策略含验证码校验，Manager负责Feign调用 */
    public LoginRespDTO loginBySms(SmsLoginReqDTO req) {
        LoginRespDTO resp = authClient.loginBySms(req);
        if (resp == null) {
            throw new org.noLazy.common.exception.BizException(
                    org.noLazy.common.enums.ErrorCode.USER_NOT_FOUND);
        }
        return resp;
    }

    /** 微信登录: Manager编排 → Feign获取微信用户 → Domain注册/登录 */
    public LoginRespDTO loginByWechat(WechatLoginReqDTO req) {
        LoginRespDTO resp = authClient.loginByWechat(req);
        if (resp == null) {
            throw new org.noLazy.common.exception.BizException(
                    org.noLazy.common.enums.ErrorCode.USER_NOT_FOUND);
        }
        return resp;
    }

    /** V4.0 DDD: 注册 — Manager编排 Feign调用 */
    @Transactional(rollbackFor = Exception.class)
    public LoginRespDTO register(RegisterReqDTO req) {
        return authClient.register(req);
    }

    /** V4.0 DDD: 重置密码 — Manager编排 Feign调用 */
    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(ResetPasswordReqDTO req) {
        authClient.resetPassword(req);
    }
}
