package org.icedAmericanoMall.service.auth.login;

import org.icedAmericanoMall.client.AuthClient;
import org.icedAmericanoMall.domain.dto.auth.LoginReq;
import org.icedAmericanoMall.domain.enums.CredentialTypeEnum;
import org.icedAmericanoMall.domain.enums.IdentityTypeEnum;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.noLazy.common.exception.BizException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * WechatLoginStrategy 单元测试 —— support 匹配与空 code 校验。
 */
@DisplayName("WechatLoginStrategy 单元测试")
class WechatLoginStrategyTest {

    private final WechatLoginStrategy strategy =
            new WechatLoginStrategy(mock(AuthClient.class), mock(LoginTokenService.class));

    @Test
    @DisplayName("support — 仅匹配 WECHAT + OAUTH_TOKEN")
    void shouldSupportWechatOauthOnly() {
        assertTrue(strategy.support(IdentityTypeEnum.WECHAT, CredentialTypeEnum.OAUTH_TOKEN));
        assertFalse(strategy.support(IdentityTypeEnum.PHONE, CredentialTypeEnum.SMS_CODE));
        assertFalse(strategy.support(IdentityTypeEnum.WECHAT, CredentialTypeEnum.PASSWORD));
    }

    @Test
    @DisplayName("login — 空 code 抛出异常")
    void shouldThrow_whenBlankCode() {
        LoginReq req = new LoginReq();
        req.setIdentityType(IdentityTypeEnum.WECHAT);
        req.setCredentialType(CredentialTypeEnum.OAUTH_TOKEN);
        req.setAccount("x");
        req.setCredential("");
        assertThrows(BizException.class, () -> strategy.login(req));
    }
}
