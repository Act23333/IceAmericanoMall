package org.icedamericanomall.auth.login;

import org.icedamericanomall.auth.domain.ability.auth.impl.WechatWebLoginStrategy;
import org.icedamericanomall.auth.domain.enums.CredentialTypeEnum;
import org.icedamericanomall.auth.domain.enums.PlatformEnum;
import org.icedamericanomall.auth.domain.gateway.WechatWebLoginGateway;
import org.icedamericanomall.auth.domain.model.LoginSpec;
import org.icedamericanomall.auth.domain.model.UserAuth;
import org.icedamericanomall.auth.domain.model.UserIdentity;
import org.icedamericanomall.common.enums.IdentityTypeEnum;
import org.icedamericanomall.common.exception.BizException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * WechatWebLoginStrategy 单元测试 —— 委托网关登录并把身份装配成认证凭证。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("WechatWebLoginStrategy 单元测试")
class WechatWebLoginStrategyTest {

    @Mock
    private WechatWebLoginGateway wechatWebLoginGateway;

    @InjectMocks
    private WechatWebLoginStrategy strategy;

    @Test
    @DisplayName("support — 仅匹配 WECHAT + WECHAT_WEB_CODE")
    void shouldSupportWechatWebOnly() {
        assertTrue(strategy.support(IdentityTypeEnum.WECHAT, CredentialTypeEnum.WECHAT_WEB_CODE));
        assertFalse(strategy.support(IdentityTypeEnum.WECHAT, CredentialTypeEnum.OAUTH_TOKEN));
        assertFalse(strategy.support(IdentityTypeEnum.PHONE, CredentialTypeEnum.WECHAT_WEB_CODE));
    }

    @Test
    @DisplayName("网页微信登录 — 网关返回身份 → 装配为认证凭证")
    void shouldReturnUserAuth_whenGatewayReturnsIdentity() {
        when(wechatWebLoginGateway.loginByWebCode("wx-code", "state-1"))
                .thenReturn(new UserIdentity(100L, "微信用户"));

        UserAuth result = strategy.login(spec("wx-code", "state-1"));

        assertEquals(100L, result.getUserId());
        assertEquals(IdentityTypeEnum.WECHAT, result.getIdentityType());
        verify(wechatWebLoginGateway).loginByWebCode("wx-code", "state-1");
    }

    @Test
    @DisplayName("网页微信登录 — code 为空 → 抛出业务异常")
    void shouldThrow_whenCodeBlank() {
        assertThrows(BizException.class, () -> strategy.login(spec("  ", null)));

        verifyNoInteractions(wechatWebLoginGateway);
    }

    @Test
    @DisplayName("网页微信登录 — 网关未返回 userId → 抛出业务异常")
    void shouldThrow_whenUserIdMissing() {
        when(wechatWebLoginGateway.loginByWebCode("wx-code", null))
                .thenReturn(new UserIdentity(null, null));

        assertThrows(BizException.class, () -> strategy.login(spec("wx-code", null)));
    }

    private LoginSpec spec(String code, String state) {
        Map<String, Object> extra = state == null ? Map.of() : Map.of("state", state);
        return new LoginSpec(IdentityTypeEnum.WECHAT, null, CredentialTypeEnum.WECHAT_WEB_CODE, code,
                PlatformEnum.WECHAT_WEB, null, null, "JUnit", extra);
    }
}
