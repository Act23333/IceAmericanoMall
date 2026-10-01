package org.icedamericanomall.auth.domain.ability.auth.impl;

import lombok.RequiredArgsConstructor;
import org.icedamericanomall.auth.domain.ability.auth.LoginStrategy;
import org.icedamericanomall.auth.domain.enums.CredentialTypeEnum;
import org.icedamericanomall.auth.domain.model.LoginSpec;
import org.icedamericanomall.auth.domain.model.UserAuth;
import org.icedamericanomall.auth.domain.model.WechatSession;
import org.icedamericanomall.auth.domain.port.WechatMiniSessionService;
import org.icedamericanomall.auth.domain.service.UserAuthProvisioner;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.enums.IdentityTypeEnum;
import org.icedamericanomall.common.exception.BizException;

/**
 * 微信小程序登录策略（领域能力）。
 *
 * <p>WECHAT + WECHAT_MINI_CODE：用 {@code wx.login()} 的 code 换取 openid / unionid，
 * 先按 unionid（跨应用打通）再按 openid 查找本地凭证，都不存在则创建。
 *
 * <p>第三方换取与用户创建均为远程调用，<b>不放在事务内</b>：本地写入仅单条
 * INSERT（天然原子），并发冲突由唯一索引兜底（见 {@link UserAuthProvisioner}）。
 */
@RequiredArgsConstructor
public class WechatMiniLoginStrategy implements LoginStrategy {

    private final WechatMiniSessionService wechatMiniSessionService;
    private final UserAuthProvisioner userAuthProvisioner;

    @Override
    public boolean support(IdentityTypeEnum identityType, CredentialTypeEnum credentialType) {
        return identityType == IdentityTypeEnum.WECHAT_MINI
                && credentialType == CredentialTypeEnum.WECHAT_MINI_CODE;
    }

    @Override
    public UserAuth login(LoginSpec spec) {
        String code = spec.credential();
        if (code == null || code.isBlank()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "微信授权 code 不能为空");
        }
        // 1. 远程换取会话标识（code → openid / unionid）
        WechatSession session = wechatMiniSessionService.exchange(code, spec.extra("phoneCode"));
        if (session.openid() == null || session.openid().isBlank()) {
            throw new BizException(ErrorCode.WECHAT_AUTH_FAILED, "微信授权失败：未获取到 openid");
        }
        // 2. 按 unionid / openid 查找或创建本地认证凭证
        return userAuthProvisioner.findByPlainOrCreate(
                IdentityTypeEnum.WECHAT_MINI, session.openid(), session.unionId());
    }
}
