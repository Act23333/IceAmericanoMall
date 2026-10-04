package org.icedamericanomall.auth.domain.ability.auth;

import org.icedamericanomall.auth.domain.ability.auth.LoginStrategy;
import org.icedamericanomall.auth.domain.ability.auth.LoginStrategyFactory;
import org.icedamericanomall.auth.domain.enums.CredentialTypeEnum;
import org.icedamericanomall.auth.domain.model.LoginSpec;
import org.icedamericanomall.auth.domain.model.userauth.UserAuth;
import org.icedamericanomall.common.enums.IdentityTypeEnum;
import org.icedamericanomall.common.exception.BizException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * LoginStrategyFactory 单元测试 —— 策略分发与不支持组合的异常。
 */
@DisplayName("LoginStrategyFactory 单元测试")
class LoginStrategyFactoryTest {

    @Test
    @DisplayName("策略分发 — 用户名+密码 → 返回密码策略")
    void shouldReturnPasswordStrategy_whenUsernamePasswordLogin() {
        LoginStrategy passwordStrategy = new StubStrategy(IdentityTypeEnum.USERNAME, CredentialTypeEnum.PASSWORD);
        LoginStrategyFactory factory = new LoginStrategyFactory(List.of(passwordStrategy));

        LoginStrategy result = factory.getStrategy(IdentityTypeEnum.USERNAME, CredentialTypeEnum.PASSWORD);

        assertSame(passwordStrategy, result);
    }

    @Test
    @DisplayName("策略分发 — 手机号+短信验证码 → 返回短信策略")
    void shouldReturnSmsStrategy_whenPhoneSmsLogin() {
        LoginStrategy passwordStrategy = new StubStrategy(IdentityTypeEnum.USERNAME, CredentialTypeEnum.PASSWORD);
        LoginStrategy smsStrategy = new StubStrategy(IdentityTypeEnum.PHONE, CredentialTypeEnum.SMS_CODE);
        LoginStrategyFactory factory = new LoginStrategyFactory(List.of(passwordStrategy, smsStrategy));

        assertSame(smsStrategy, factory.getStrategy(IdentityTypeEnum.PHONE, CredentialTypeEnum.SMS_CODE));
        assertSame(passwordStrategy, factory.getStrategy(IdentityTypeEnum.USERNAME, CredentialTypeEnum.PASSWORD));
    }

    @Test
    @DisplayName("策略分发 — 不支持的组合 → 抛出业务异常")
    void shouldThrowBizException_whenStrategyUnsupported() {
        LoginStrategyFactory factory = new LoginStrategyFactory(List.of(
                new StubStrategy(IdentityTypeEnum.USERNAME, CredentialTypeEnum.PASSWORD)
        ));

        assertThrows(BizException.class,
                () -> factory.getStrategy(IdentityTypeEnum.WECHAT, CredentialTypeEnum.WECHAT_WEB_CODE));
    }

    /** 桩策略：只匹配指定组合 */
    private record StubStrategy(IdentityTypeEnum identityType, CredentialTypeEnum credentialType)
            implements LoginStrategy {

        @Override
        public boolean support(IdentityTypeEnum identityType, CredentialTypeEnum credentialType) {
            return this.identityType == identityType && this.credentialType == credentialType;
        }

        @Override
        public UserAuth login(LoginSpec spec) {
            throw new UnsupportedOperationException("桩策略不执行登录");
        }
    }
}
