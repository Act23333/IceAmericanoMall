package org.icedamericanomall.auth.domain.ability.auth;

import org.icedamericanomall.auth.domain.enums.CredentialTypeEnum;
import org.icedamericanomall.auth.domain.model.LoginSpec;
import org.icedamericanomall.auth.domain.model.UserAuth;
import org.icedamericanomall.common.enums.IdentityTypeEnum;

/**
 * 登录策略（领域能力）—— 每种登录方式一个实现。
 *
 * <p>只判断登录方式（身份类型 + 凭证类型），不判断平台；
 * 实现类是纯 POJO，由基础设施层的配置类注册为 Bean。
 */
public interface LoginStrategy {

    /**
     * 判断是否支持该登录方式。
     * 只判断登录方式，不判断平台。
     */
    boolean support(IdentityTypeEnum identityType, CredentialTypeEnum credentialType);

    /**
     * 执行登录：校验凭证并返回对应的认证凭证聚合。
     *
     * @param spec 本次登录尝试的领域入参
     * @return 通过校验的认证凭证
     */
    UserAuth login(LoginSpec spec);
}
