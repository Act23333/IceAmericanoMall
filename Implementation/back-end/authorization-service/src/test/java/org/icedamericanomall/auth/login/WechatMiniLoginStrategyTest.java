package org.icedamericanomall.auth.login;

import org.icedamericanomall.auth.domain.ability.auth.impl.WechatMiniLoginStrategy;
import org.icedamericanomall.auth.domain.enums.CredentialTypeEnum;
import org.icedamericanomall.auth.domain.enums.PlatformEnum;
import org.icedamericanomall.auth.domain.model.LoginSpec;
import org.icedamericanomall.auth.domain.model.UserAuth;
import org.icedamericanomall.auth.domain.model.WechatSession;
import org.icedamericanomall.auth.domain.port.WechatMiniSessionService;
import org.icedamericanomall.auth.domain.service.UserAuthProvisioner;
import org.icedamericanomall.common.enums.IdentityTypeEnum;
import org.icedamericanomall.common.exception.BizException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * WechatMiniLoginStrategy 单元测试 —— code 换取会话 + 按 openid/unionId 查找或创建。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("WechatMiniLoginStrategy 单元测试")
class WechatMiniLoginStrategyTest {

    @Mock
    private WechatMiniSessionService wechatMiniSessionService;

    @Mock
    private UserAuthProvisioner userAuthProvisioner;

    @InjectMocks
    private WechatMiniLoginStrategy strategy;

    @Test
    @DisplayName("support — 仅匹配 WECHAT_MINI + WECHAT_MINI_CODE")
    void shouldSupportWechatMiniOnly() {
        assertTrue(strategy.support(IdentityTypeEnum.WECHAT_MINI, CredentialTypeEnum.WECHAT_MINI_CODE));
        assertFalse(strategy.support(IdentityTypeEnum.WECHAT_MINI, CredentialTypeEnum.MINI_CODE));
        assertFalse(strategy.support(IdentityTypeEnum.WECHAT, CredentialTypeEnum.WECHAT_WEB_CODE));
    }

    @Test
    @DisplayName("小程序登录 — 换取到 openid → 按 openid/unionId 查找或创建本地凭证")
    void shouldProvisionByOpenIdAndUnionId_whenSessionExchanged() {
        when(wechatMiniSessionService.exchange("wx-code", "phone-code"))
                .thenReturn(new WechatSession("openid-1", "unionid-1", null));
        UserAuth userAuth = UserAuth.builder()
                .userId(100L)
                .identityType(IdentityTypeEnum.WECHAT_MINI)
                .identifierPlain("openid-1")
                .build();
        when(userAuthProvisioner.findByPlainOrCreate(IdentityTypeEnum.WECHAT_MINI, "openid-1", "unionid-1"))
                .thenReturn(userAuth);

        UserAuth result = strategy.login(spec("wx-code", "phone-code"));

        assertSame(userAuth, result);
        verify(wechatMiniSessionService).exchange("wx-code", "phone-code");
        verify(userAuthProvisioner).findByPlainOrCreate(IdentityTypeEnum.WECHAT_MINI, "openid-1", "unionid-1");
    }

    @Test
    @DisplayName("小程序登录 — code 为空 → 抛出业务异常")
    void shouldThrow_whenCodeBlank() {
        assertThrows(BizException.class, () -> strategy.login(spec("", null)));

        verifyNoInteractions(wechatMiniSessionService, userAuthProvisioner);
    }

    @Test
    @DisplayName("小程序登录 — 未取到 openid → 抛出业务异常")
    void shouldThrow_whenOpenidMissing() {
        when(wechatMiniSessionService.exchange("wx-code", null))
                .thenReturn(new WechatSession(null, null, null));

        assertThrows(BizException.class, () -> strategy.login(spec("wx-code", null)));

        verifyNoInteractions(userAuthProvisioner);
    }

    private LoginSpec spec(String code, String phoneCode) {
        Map<String, Object> extra = phoneCode == null ? Map.of() : Map.of("phoneCode", phoneCode);
        return new LoginSpec(IdentityTypeEnum.WECHAT_MINI, null, CredentialTypeEnum.WECHAT_MINI_CODE, code,
                PlatformEnum.WECHAT_MINI, null, null, "JUnit", extra);
    }
}
