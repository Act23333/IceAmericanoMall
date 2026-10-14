package org.icedamericanomall.auth.domain.ability.auth.impl;

import org.icedamericanomall.auth.domain.ability.auth.impl.SmsLoginStrategy;
import org.icedamericanomall.auth.domain.enums.CredentialTypeEnum;
import org.icedamericanomall.auth.domain.enums.PlatformEnum;
import org.icedamericanomall.auth.domain.enums.VerificationChannelEnum;
import org.icedamericanomall.auth.domain.enums.VerificationSceneEnum;
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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * SmsLoginStrategy 单元测试 —— 验证码消费 + 查找/创建本地凭证。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SmsLoginStrategy 单元测试")
class SmsLoginStrategyTest {

    @Mock
    private VerificationCodeService verificationCodeService;

    @Mock
    private UserAuthProvisioner userAuthProvisioner;

    @InjectMocks
    private SmsLoginStrategy strategy;

    @BeforeAll
    static void initCrypto() {
        BlindIndexUtil.init(Base64.getEncoder().encodeToString(new byte[32]));
    }

    @Test
    @DisplayName("手机号验证码登录 — 验证码合法 → 消费验证码并查找/创建凭证")
    void shouldVerifyCodeAndProvision_whenCodeValid() {
        UserAuth userAuth = UserAuth.builder().userId(100L).identityType(IdentityTypeEnum.PHONE).build();
        when(userAuthProvisioner.findByHashOrCreate(eq(IdentityTypeEnum.PHONE), anyString(), eq("13888888888")))
                .thenReturn(userAuth);

        UserAuth result = strategy.login(spec("13888888888", "123456"));

        assertSame(userAuth, result);
        verify(verificationCodeService)
                .verifyAndConsume(VerificationSceneEnum.LOGIN, VerificationChannelEnum.SMS, "13888888888", "123456");
        verify(userAuthProvisioner).findByHashOrCreate(eq(IdentityTypeEnum.PHONE), anyString(), eq("13888888888"));
    }

    @Test
    @DisplayName("手机号验证码登录 — 验证码格式错误 → 不消费验证码")
    void shouldReject_whenCodeFormatInvalid() {
        assertThrows(BizException.class, () -> strategy.login(spec("13888888888", "abc")));

        verifyNoInteractions(verificationCodeService, userAuthProvisioner);
    }

    @Test
    @DisplayName("手机号验证码登录 — 手机号格式错误 → 直接拒绝")
    void shouldReject_whenPhoneFormatInvalid() {
        assertThrows(BizException.class, () -> strategy.login(spec("12345", "123456")));

        verifyNoInteractions(verificationCodeService, userAuthProvisioner);
    }

    @Test
    @DisplayName("手机号验证码登录 — 验证码错误 → 抛出业务异常且不创建凭证")
    void shouldPropagateException_whenCodeRejected() {
        doThrow(new BizException(ErrorCode.CAPTCHA_ERROR, "验证码错误"))
                .when(verificationCodeService)
                .verifyAndConsume(VerificationSceneEnum.LOGIN, VerificationChannelEnum.SMS, "13888888888", "999999");

        BizException exception = assertThrows(BizException.class,
                () -> strategy.login(spec("13888888888", "999999")));

        assertEquals(ErrorCode.CAPTCHA_ERROR, exception.getErrorCode());
        verifyNoInteractions(userAuthProvisioner);
    }

    private LoginSpec spec(String phone, String code) {
        return new LoginSpec(IdentityTypeEnum.PHONE, phone, CredentialTypeEnum.SMS_CODE, code,
                PlatformEnum.H5, "device-1", "127.0.0.1", "JUnit", Map.of());
    }
}
