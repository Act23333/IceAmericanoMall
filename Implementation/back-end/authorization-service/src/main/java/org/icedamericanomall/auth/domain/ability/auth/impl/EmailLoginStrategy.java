package org.icedamericanomall.auth.domain.ability.auth.impl;

import lombok.RequiredArgsConstructor;
import org.icedamericanomall.auth.domain.ability.auth.LoginStrategy;
import org.icedamericanomall.auth.domain.enums.CredentialTypeEnum;
import org.icedamericanomall.auth.domain.enums.VerificationChannelEnum;
import org.icedamericanomall.auth.domain.enums.VerificationSceneEnum;
import org.icedamericanomall.auth.domain.model.EmailAddress;
import org.icedamericanomall.auth.domain.model.LoginSpec;
import org.icedamericanomall.auth.domain.model.userauth.UserAuth;
import org.icedamericanomall.auth.domain.port.VerificationCodeService;
import org.icedamericanomall.auth.domain.service.UserAuthProvisioner;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.enums.IdentityTypeEnum;
import org.icedamericanomall.common.exception.BizException;

/**
 * 邮箱验证码登录策略（领域能力）。
 *
 * <p>仅支持 EMAIL + EMAIL_CODE：邮箱格式校验 → 校验并消费邮箱验证码 → 查找或创建本地凭证。
 * 顺序上<b>先本地格式校验、后远程校验</b>：非法邮箱不消耗验证码、不打下游。
 *
 * <p>"验证码校验"与"凭证注册"分别由端口与领域服务承担：
 * <ul>
 *   <li>验证码：委托 verification-service（本上下文不生成、不存储验证码）</li>
 *   <li>凭证：{@link UserAuthProvisioner} 按盲索引查找，不存在则创建
 *       （邮箱首次验证码登录即注册；EMAIL+PASSWORD 密码登录由
 *       {@link PasswordLoginStrategy} 承担，两者互不影响）</li>
 * </ul>
 *
 * <p>无本地事务：验证码消费与凭证写入各自原子，失败时由下次登录重放（幂等）。
 */
@RequiredArgsConstructor
public class EmailLoginStrategy implements LoginStrategy {

    /** 验证码格式：6 位数字（与 verification-service 签发策略一致） */
    private static final String CODE_PATTERN = "^\\d{6}$";

    private final VerificationCodeService verificationCodeService;
    private final UserAuthProvisioner userAuthProvisioner;

    @Override
    public boolean support(IdentityTypeEnum identityType, CredentialTypeEnum credentialType) {
        return identityType == IdentityTypeEnum.EMAIL && credentialType == CredentialTypeEnum.EMAIL_CODE;
    }

    @Override
    public UserAuth login(LoginSpec spec) {
        // 1. 邮箱格式校验 + 归一化（值对象自封装；非法格式不触发远程调用）
        EmailAddress email = EmailAddress.of(spec.identifier());

        // 2. 验证码格式校验（避免用明显非法的凭证消耗远端错误次数）
        if (spec.credential() == null || !spec.credential().matches(CODE_PATTERN)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "验证码格式不正确");
        }

        // 3. 校验并消费验证码（远程校验 + 一次性消费）
        verificationCodeService.verifyAndConsume(
                VerificationSceneEnum.LOGIN, VerificationChannelEnum.EMAIL, email.value(), spec.credential());

        // 4. 按盲索引查找或创建本地认证凭证
        return userAuthProvisioner.findByHashOrCreate(
                IdentityTypeEnum.EMAIL, email.blindIndex(), email.value());
    }
}
