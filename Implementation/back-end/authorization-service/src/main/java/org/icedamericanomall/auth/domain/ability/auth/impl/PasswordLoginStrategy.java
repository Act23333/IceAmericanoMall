package org.icedamericanomall.auth.domain.ability.auth.impl;

import lombok.RequiredArgsConstructor;
import org.icedamericanomall.auth.domain.ability.auth.LoginStrategy;
import org.icedamericanomall.auth.domain.enums.CredentialTypeEnum;
import org.icedamericanomall.auth.domain.model.LoginSpec;
import org.icedamericanomall.auth.domain.model.UserAuth;
import org.icedamericanomall.auth.domain.port.PasswordVerifier;
import org.icedamericanomall.auth.domain.repository.UserAuthRepository;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.enums.IdentityTypeEnum;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.common.utils.crypto.BlindIndexUtil;

import java.util.Optional;

/**
 * 密码登录策略（领域能力）。
 *
 * <p>支持 USERNAME / PHONE / EMAIL 三种身份类型 + 密码凭证：
 * <ul>
 *   <li>PHONE / EMAIL：敏感标识，走盲索引（HMAC）查询</li>
 *   <li>USERNAME：非敏感标识，走明文查询</li>
 * </ul>
 * 密码哈希比较通过 {@link PasswordVerifier} 端口注入，领域层不依赖 Spring Security。
 */
@RequiredArgsConstructor
public class PasswordLoginStrategy implements LoginStrategy {

    private final UserAuthRepository userAuthRepository;
    private final PasswordVerifier passwordVerifier;

    @Override
    public boolean support(IdentityTypeEnum identityType, CredentialTypeEnum credentialType) {
        return credentialType == CredentialTypeEnum.PASSWORD
                && (identityType == IdentityTypeEnum.USERNAME
                || identityType == IdentityTypeEnum.PHONE
                || identityType == IdentityTypeEnum.EMAIL);
    }

    @Override
    public UserAuth login(LoginSpec spec) {
        // 1. 按身份类型查询认证凭证（账号不存在与密码错误统一提示，避免账号枚举）
        UserAuth userAuth = findUserAuth(spec);

        // 2. 校验密码
        if (!userAuth.verifyPassword(spec.credential(), passwordVerifier)) {
            throw new BizException(ErrorCode.PASSWORD_ERROR, "账号或密码错误");
        }

        // 3. 校验账号状态
        userAuth.assertActive();

        return userAuth;
    }

    /**
     * 根据 identityType 决定查询方式。
     * <ul>
     *   <li>PHONE / EMAIL：走盲索引查询</li>
     *   <li>USERNAME：走明文字段查询</li>
     * </ul>
     */
    private UserAuth findUserAuth(LoginSpec spec) {
        IdentityTypeEnum type = spec.identityType();
        Optional<UserAuth> found = switch (type) {
            case PHONE, EMAIL -> userAuthRepository.findByIdentityHash(
                    type, BlindIndexUtil.hmac(spec.identifier()));
            case USERNAME -> userAuthRepository.findByIdentity(type, spec.identifier());
            default -> throw new BizException(
                    ErrorCode.PARAM_ERROR, "不支持的登录身份类型: " + type);
        };
        return found.orElseThrow(() ->
                new BizException(ErrorCode.USER_NOT_FOUND, "账号或密码错误"));
    }
}
