package org.icedamericanomall.auth.domain.ability.auth;

import lombok.RequiredArgsConstructor;
import org.icedamericanomall.auth.domain.enums.CredentialTypeEnum;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.enums.IdentityTypeEnum;
import org.icedamericanomall.common.exception.BizException;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 登录策略工厂（领域能力）—— 按"身份类型 + 凭证类型"分发策略。
 *
 * <p>启动时一次性建立索引，避免每次登录都遍历列表。
 */
@RequiredArgsConstructor
public class LoginStrategyFactory {

    private final List<LoginStrategy> strategies;

    /** 登录方式 → 策略 的索引，首次调用时构建（策略集合在启动后不再变化） */
    private volatile Map<IdentityTypeEnum, Map<CredentialTypeEnum, LoginStrategy>> index;

    /**
     * 获取匹配的登录策略。
     *
     * @param identityType   身份类型
     * @param credentialType 凭证类型
     * @return 匹配的策略
     * @throws BizException 无匹配策略（不支持的登录方式）
     */
    public LoginStrategy getStrategy(IdentityTypeEnum identityType, CredentialTypeEnum credentialType) {
        LoginStrategy strategy = strategyIndex().getOrDefault(identityType, Map.of()).get(credentialType);
        if (strategy == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "不支持的登录方式");
        }
        return strategy;
    }

    private Map<IdentityTypeEnum, Map<CredentialTypeEnum, LoginStrategy>> strategyIndex() {
        Map<IdentityTypeEnum, Map<CredentialTypeEnum, LoginStrategy>> local = this.index;
        if (local == null) {
            synchronized (this) {
                local = this.index;
                if (local == null) {
                    local = buildIndex();
                    this.index = local;
                }
            }
        }
        return local;
    }

    private Map<IdentityTypeEnum, Map<CredentialTypeEnum, LoginStrategy>> buildIndex() {
        Map<IdentityTypeEnum, Map<CredentialTypeEnum, LoginStrategy>> map = new EnumMap<>(IdentityTypeEnum.class);
        for (LoginStrategy strategy : strategies) {
            for (IdentityTypeEnum identityType : IdentityTypeEnum.values()) {
                for (CredentialTypeEnum credentialType : CredentialTypeEnum.values()) {
                    if (strategy.support(identityType, credentialType)) {
                        map.computeIfAbsent(identityType, k -> new EnumMap<>(CredentialTypeEnum.class))
                                .put(credentialType, strategy);
                    }
                }
            }
        }
        return map;
    }
}
