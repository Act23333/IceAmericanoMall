package org.icedAmericanoMall.service.auth.login;

import org.icedAmericanoMall.domain.dto.OAuth2TokenResp;
import org.icedAmericanoMall.domain.dto.auth.LoginReq;
import org.icedAmericanoMall.domain.enums.CredentialTypeEnum;
import org.icedAmericanoMall.domain.enums.IdentityTypeEnum;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.noLazy.common.exception.BizException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LoginStrategyFactoryTest {

    @Test
    @DisplayName("登录策略选择 - 用户名密码 -> 返回密码策略")
    void shouldReturnPasswordStrategy_whenUsernamePasswordLogin() {
        LoginStrategy passwordStrategy = new StubStrategy(IdentityTypeEnum.USERNAME, CredentialTypeEnum.PASSWORD);
        LoginStrategyFactory factory = new LoginStrategyFactory(List.of(passwordStrategy));

        LoginStrategy result = factory.getStrategy(IdentityTypeEnum.USERNAME, CredentialTypeEnum.PASSWORD);

        assertSame(passwordStrategy, result);
    }

    @Test
    @DisplayName("登录策略选择 - 不支持的组合 -> 抛出业务异常")
    void shouldThrowBizException_whenStrategyUnsupported() {
        LoginStrategyFactory factory = new LoginStrategyFactory(List.of(
                new StubStrategy(IdentityTypeEnum.USERNAME, CredentialTypeEnum.PASSWORD)
        ));

        assertThrows(BizException.class,
                () -> factory.getStrategy(IdentityTypeEnum.WECHAT, CredentialTypeEnum.OAUTH_TOKEN));
    }

    private record StubStrategy(IdentityTypeEnum identityType, CredentialTypeEnum credentialType)
            implements LoginStrategy {

        @Override
        public boolean support(IdentityTypeEnum identityType, CredentialTypeEnum credentialType) {
            return this.identityType == identityType && this.credentialType == credentialType;
        }

        @Override
        public OAuth2TokenResp login(LoginReq request) {
            return new OAuth2TokenResp();
        }
    }
}
