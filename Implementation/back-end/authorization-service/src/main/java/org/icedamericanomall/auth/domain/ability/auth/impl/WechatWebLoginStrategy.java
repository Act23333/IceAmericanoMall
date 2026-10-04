package org.icedamericanomall.auth.domain.ability.auth.impl;

import lombok.RequiredArgsConstructor;
import org.icedamericanomall.auth.domain.ability.auth.LoginStrategy;
import org.icedamericanomall.auth.domain.enums.CredentialTypeEnum;
import org.icedamericanomall.auth.domain.model.LoginSpec;
import org.icedamericanomall.auth.domain.model.userauth.UserAuth;
import org.icedamericanomall.auth.domain.model.WechatSession;
import org.icedamericanomall.auth.domain.port.WechatWebSessionService;
import org.icedamericanomall.auth.domain.service.UserAuthProvisioner;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.enums.IdentityTypeEnum;
import org.icedamericanomall.common.exception.BizException;

/**
 * 微信网页（OAuth2）登录策略（领域能力）。
 *
 * <p>WECHAT + WECHAT_WEB_CODE：{@code credential} 承载前端授权回调拿到的 code。
 *
 * <p><b>V5.2 认证边界收敛（ADR-017）</b>：code→openid 的换取改由本上下文直连微信开放平台
 * （{@link WechatWebSessionService}），凭证建档由 {@link UserAuthProvisioner} 承担
 * （无凭证、openid 明文唯一键）—— 不再委托 user-service 的登录端点，
 * 与 {@link WechatMiniLoginStrategy} 完全对称。
 *
 * <p>冻结语义对齐说明：user 资料级冻结（{@code user.status=FROZEN}）的拦截点在
 * 下游业务 API 与风控处置，本策略与密码/短信/邮箱策略一致只校验凭证状态
 * （{@code user_auth.status}）；旧实现中"微信登录额外回查 user 冻结状态"的行为随
 * 端点废弃而退出（行为变化已在 ADR-017 声明）。
 */
@RequiredArgsConstructor
public class WechatWebLoginStrategy implements LoginStrategy {

    private final WechatWebSessionService wechatWebSessionService;
    private final UserAuthProvisioner userAuthProvisioner;

    @Override
    public boolean support(IdentityTypeEnum identityType, CredentialTypeEnum credentialType) {
        return identityType == IdentityTypeEnum.WECHAT
                && credentialType == CredentialTypeEnum.WECHAT_WEB_CODE;
    }

    @Override
    public UserAuth login(LoginSpec spec) {
        String code = spec.credential();
        if (code == null || code.isBlank()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "微信授权 code 不能为空");
        }
        // 1. 直连微信开放平台换取会话标识（code → openid / nickname）
        WechatSession session = wechatWebSessionService.exchange(code, spec.extra("state"));
        if (session.openid() == null || session.openid().isBlank()) {
            throw new BizException(ErrorCode.WECHAT_AUTH_FAILED, "微信授权失败：未获取到 openid");
        }
        // 2. 按 openid 查找或创建本地认证凭证（首次即建档：user-service 幂等建档 + 建档奖励）
        return userAuthProvisioner.findByPlainOrCreate(
                IdentityTypeEnum.WECHAT, session.openid(), session.unionId(), session.nickname());
    }
}
