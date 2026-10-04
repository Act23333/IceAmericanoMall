package org.icedamericanomall.auth.domain.ability.auth.impl;

import org.icedamericanomall.auth.domain.ability.auth.LoginStrategy;
import org.icedamericanomall.auth.domain.ability.auth.LoginStrategyFactory;
import org.icedamericanomall.auth.domain.ability.auth.impl.EmailLoginStrategy;
import org.icedamericanomall.auth.domain.ability.auth.impl.PasswordLoginStrategy;
import org.icedamericanomall.auth.domain.enums.CredentialTypeEnum;
import org.icedamericanomall.auth.domain.enums.PlatformEnum;
import org.icedamericanomall.auth.domain.enums.VerificationChannelEnum;
import org.icedamericanomall.auth.domain.model.EmailAddress;
import org.icedamericanomall.auth.domain.model.LoginSpec;
import org.icedamericanomall.auth.domain.model.userauth.UserAuth;
import org.icedamericanomall.auth.domain.port.VerificationCodeService;
import org.icedamericanomall.auth.domain.service.UserAuthProvisioner;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.enums.IdentityTypeEnum;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.common.utils.crypto.BlindIndexUtil;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * EmailLoginStrategy 单元测试 —— 邮箱验证码登录（新能力）。
 *
 * <p>覆盖：策略路由（EMAIL + EMAIL_CODE）、验证码正确放行、验证码错误拒登、
 * 邮箱非法/验证码格式非法时<b>不</b>打远程（避免消耗远端错误次数）。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EmailLoginStrategy 单元测试")
class EmailLoginStrategyTest {

    @Mock
    private VerificationCodeService verificationCodeService;

    @Mock
    private UserAuthProvisioner userAuthProvisioner;

    @InjectMocks
    private EmailLoginStrategy strategy;

    @BeforeAll
    static void initCrypto() {
        BlindIndexUtil.init(Base64.getEncoder().encodeToString(new byte[32]));
    }

    @Test
    @DisplayName("策略路由 — 邮箱 + 邮箱验证码 → 由 LoginStrategyFactory 分发到本策略")
    void shouldRouteToEmailStrategy_whenEmailAndEmailCode() {
        LoginStrategyFactory factory = new LoginStrategyFactory(List.of(
                new PasswordLoginStrategy(null, null), strategy));

        LoginStrategy routed = factory.getStrategy(IdentityTypeEnum.EMAIL, CredentialTypeEnum.EMAIL_CODE);

        assertSame(strategy, routed);
    }

    @Test
    @DisplayName("邮箱验证码登录 — 验证码合法 → 消费验证码并按盲索引查找/创建凭证")
    void shouldVerifyCodeAndProvision_whenCodeValid() {
        UserAuth userAuth = UserAuth.builder().userId(200L).identityType(IdentityTypeEnum.EMAIL).build();
        when(userAuthProvisioner.findByHashOrCreate(
                eq(IdentityTypeEnum.EMAIL), anyString(), eq("buyer@example.com")))
                .thenReturn(userAuth);

        UserAuth result = strategy.login(spec("buyer@example.com", "123456"));

        assertSame(userAuth, result);
        verify(verificationCodeService)
                .verifyAndConsume(VerificationChannelEnum.EMAIL, "buyer@example.com", "123456");
        verify(userAuthProvisioner)
                .findByHashOrCreate(eq(IdentityTypeEnum.EMAIL), anyString(), eq("buyer@example.com"));
    }

    @Test
    @DisplayName("邮箱验证码登录 — 大小写归一 → 用归一化邮箱消费验证码（与发码 Key 对齐）")
    void shouldNormalizeEmail_whenMixedCase() {
        when(userAuthProvisioner.findByHashOrCreate(eq(IdentityTypeEnum.EMAIL), anyString(), anyString()))
                .thenReturn(UserAuth.builder().userId(201L).identityType(IdentityTypeEnum.EMAIL).build());

        strategy.login(spec("Buyer@Example.COM", "123456"));

        verify(verificationCodeService)
                .verifyAndConsume(VerificationChannelEnum.EMAIL, "buyer@example.com", "123456");
    }

    @Test
    @DisplayName("邮箱验证码登录 — 验证码错误 → 拒登且不创建凭证")
    void shouldReject_whenCodeRejected() {
        doThrow(new BizException(ErrorCode.CAPTCHA_ERROR, "验证码错误"))
                .when(verificationCodeService)
                .verifyAndConsume(VerificationChannelEnum.EMAIL, "buyer@example.com", "000000");

        BizException exception = assertThrows(BizException.class,
                () -> strategy.login(spec("buyer@example.com", "000000")));

        assertEquals(ErrorCode.CAPTCHA_ERROR, exception.getErrorCode());
        verifyNoInteractions(userAuthProvisioner);
    }

    @Test
    @DisplayName("邮箱验证码登录 — 邮箱格式非法 → 直接拒绝，不打远程也不建号")
    void shouldReject_whenEmailFormatInvalid() {
        assertThrows(BizException.class, () -> strategy.login(spec("not-an-email", "123456")));

        verifyNoInteractions(verificationCodeService, userAuthProvisioner);
    }

    @Test
    @DisplayName("邮箱验证码登录 — 验证码格式非法（非 6 位数字）→ 不消费验证码")
    void shouldReject_whenCodeFormatInvalid() {
        assertThrows(BizException.class, () -> strategy.login(spec("buyer@example.com", "abc")));

        verifyNoInteractions(verificationCodeService, userAuthProvisioner);
    }

    @Test
    @DisplayName("support — 仅支持 EMAIL + EMAIL_CODE，不抢占密码登录")
    void shouldSupportOnlyEmailCode() {
        assertTrue(strategy.support(IdentityTypeEnum.EMAIL, CredentialTypeEnum.EMAIL_CODE));
        assertFalse(strategy.support(IdentityTypeEnum.EMAIL, CredentialTypeEnum.PASSWORD));
        assertFalse(strategy.support(IdentityTypeEnum.PHONE, CredentialTypeEnum.EMAIL_CODE));
    }

    @Test
    @DisplayName("值对象 — 邮箱归一化与盲索引稳定（同邮箱不同大小写视为同一标识）")
    void shouldNormalizeEmailInValueObject() {
        assertEquals("buyer@example.com", EmailAddress.of(" Buyer@Example.com ").value());
        assertEquals(EmailAddress.of("buyer@example.com").blindIndex(),
                EmailAddress.of("BUYER@EXAMPLE.COM").blindIndex());
    }

    private LoginSpec spec(String email, String code) {
        return new LoginSpec(IdentityTypeEnum.EMAIL, email, CredentialTypeEnum.EMAIL_CODE, code,
                PlatformEnum.WEB, "device-1", "127.0.0.1", "JUnit", Map.of());
    }
}
