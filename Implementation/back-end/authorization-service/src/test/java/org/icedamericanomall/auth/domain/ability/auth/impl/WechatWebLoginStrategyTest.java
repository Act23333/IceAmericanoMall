package org.icedamericanomall.auth.domain.ability.auth.impl;

import org.icedamericanomall.auth.domain.ability.auth.impl.WechatWebLoginStrategy;
import org.icedamericanomall.auth.domain.enums.CredentialTypeEnum;
import org.icedamericanomall.auth.domain.enums.PlatformEnum;
import org.icedamericanomall.auth.domain.model.LoginSpec;
import org.icedamericanomall.auth.domain.model.userauth.UserAuth;
import org.icedamericanomall.auth.domain.model.WechatSession;
import org.icedamericanomall.auth.domain.port.WechatWebSessionService;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * WechatWebLoginStrategy 单元测试（V5.2 边界收敛后）——
 * code→openid 由本上下文第三方能力完成，凭证建档委托 UserAuthProvisioner
 * （不再经 user-service 登录端点）。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("WechatWebLoginStrategy 单元测试")
class WechatWebLoginStrategyTest {

    @Mock
    private WechatWebSessionService wechatWebSessionService;

    @Mock
    private UserAuthProvisioner userAuthProvisioner;

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
    @DisplayName("网页微信登录 — code 换取 openid → 查找或创建本地凭证（昵称透传建档）")
    void shouldProvisionLocalCredential_whenExchangeSucceeds() {
        when(wechatWebSessionService.exchange("wx-code", "state-1"))
                .thenReturn(new WechatSession("openid-1", "unionid-1", null, "微信用户"));
        UserAuth provisioned = UserAuth.builder().userId(100L).identityType(IdentityTypeEnum.WECHAT).build();
        when(userAuthProvisioner.findByPlainOrCreate(
                eq(IdentityTypeEnum.WECHAT), eq("openid-1"), eq("unionid-1"), eq("微信用户")))
                .thenReturn(provisioned);

        UserAuth result = strategy.login(spec("wx-code", "state-1"));

        assertEquals(100L, result.getUserId());
        assertEquals(IdentityTypeEnum.WECHAT, result.getIdentityType());
        verify(userAuthProvisioner).findByPlainOrCreate(
                IdentityTypeEnum.WECHAT, "openid-1", "unionid-1", "微信用户");
    }

    @Test
    @DisplayName("网页微信登录 — code 为空 → 抛出业务异常，不触发换取与建档")
    void shouldThrow_whenCodeBlank() {
        assertThrows(BizException.class, () -> strategy.login(spec("  ", null)));

        verifyNoInteractions(wechatWebSessionService, userAuthProvisioner);
    }

    @Test
    @DisplayName("网页微信登录 — 换取结果无 openid → 抛出业务异常，不建档")
    void shouldThrow_whenOpenidMissing() {
        when(wechatWebSessionService.exchange("wx-code", null))
                .thenReturn(new WechatSession(null, null, null, null));

        assertThrows(BizException.class, () -> strategy.login(spec("wx-code", null)));

        verify(userAuthProvisioner, org.mockito.Mockito.never())
                .findByPlainOrCreate(eq(IdentityTypeEnum.WECHAT), isNull(), isNull(), isNull());
    }

    @Test
    @DisplayName("网页微信登录 — openid 存在但无 unionId/昵称 → 仍可建档（空值安全）")
    void shouldProvision_whenUnionIdAndNicknameAbsent() {
        when(wechatWebSessionService.exchange("wx-code", null))
                .thenReturn(new WechatSession("openid-2", null, null, null));
        when(userAuthProvisioner.findByPlainOrCreate(IdentityTypeEnum.WECHAT, "openid-2", null, null))
                .thenReturn(UserAuth.builder().userId(200L).identityType(IdentityTypeEnum.WECHAT).build());

        UserAuth result = strategy.login(spec("wx-code", null));

        assertEquals(200L, result.getUserId());
    }

    private LoginSpec spec(String code, String state) {
        Map<String, Object> extra = state == null ? Map.of() : Map.of("state", state);
        return new LoginSpec(IdentityTypeEnum.WECHAT, null, CredentialTypeEnum.WECHAT_WEB_CODE, code,
                PlatformEnum.WECHAT_WEB, null, null, "JUnit", extra);
    }
}
