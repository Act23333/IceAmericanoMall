package org.icedamericanomall.auth.domain.ability.auth.impl;

import lombok.RequiredArgsConstructor;
import org.icedamericanomall.auth.domain.ability.auth.LoginStrategy;
import org.icedamericanomall.auth.domain.enums.CredentialTypeEnum;
import org.icedamericanomall.auth.domain.enums.VerificationChannelEnum;
import org.icedamericanomall.auth.domain.enums.VerificationSceneEnum;
import org.icedamericanomall.auth.domain.model.LoginSpec;
import org.icedamericanomall.auth.domain.model.userauth.UserAuth;
import org.icedamericanomall.auth.domain.port.VerificationCodeService;
import org.icedamericanomall.auth.domain.service.UserAuthProvisioner;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.enums.IdentityTypeEnum;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.common.utils.crypto.BlindIndexUtil;

/**
 * 短信验证码登录策略（领域能力）。
 *
 * <p>仅支持 PHONE + SMS_CODE：验证码校验通过后，按手机号盲索引查找本地凭证，
 * 不存在则由 {@link UserAuthProvisioner} 创建（含跨上下文取用户 ID）。
 *
 * <p>验证码校验委托 {@link VerificationCodeService}（verification-service 为签发与消费的唯一出口，
 * 码池按场景隔离：{@code sms:code:LOGIN:{phone}}，ADR-051）。
 *
 * <p>无本地事务：验证码消费与凭证写入各自原子，失败时由下次登录重放（幂等）。
 */
@RequiredArgsConstructor
public class SmsLoginStrategy implements LoginStrategy {

    private static final String PHONE_PATTERN = "^1[3-9]\\d{9}$";
    private static final String SMS_CODE_PATTERN = "^\\d{6}$";

    private final VerificationCodeService verificationCodeService;
    private final UserAuthProvisioner userAuthProvisioner;

    @Override
    public boolean support(IdentityTypeEnum identityType, CredentialTypeEnum credentialType) {
        return identityType == IdentityTypeEnum.PHONE && credentialType == CredentialTypeEnum.SMS_CODE;
    }

    @Override
    public UserAuth login(LoginSpec spec) {
        validate(spec);
        // 1. 校验并消费验证码（委托 verification-service，一次性消费）
        verificationCodeService.verifyAndConsume(
                VerificationSceneEnum.LOGIN, VerificationChannelEnum.SMS, spec.identifier(), spec.credential());
        // 2. 按盲索引查找或创建本地认证凭证
        String hash = BlindIndexUtil.hmac(spec.identifier());
        return userAuthProvisioner.findByHashOrCreate(spec.identityType(), hash, spec.identifier());
    }

    private void validate(LoginSpec spec) {
        if (spec.identityType() != IdentityTypeEnum.PHONE) {
            throw new BizException(ErrorCode.PARAM_ERROR, "短信登录必须使用手机号身份");
        }
        if (spec.identifier() == null || !spec.identifier().matches(PHONE_PATTERN)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "手机号格式不正确");
        }
        if (spec.credential() == null || !spec.credential().matches(SMS_CODE_PATTERN)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "验证码格式不正确");
        }
    }
}
