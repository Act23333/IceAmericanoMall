package org.icedamericanomall.auth.app;

import org.icedamericanomall.auth.app.command.EmailCodeCommand;
import org.icedamericanomall.auth.app.command.LoginCommand;
import org.icedamericanomall.auth.app.dto.CaptchaVerifyParam;
import org.icedamericanomall.auth.app.dto.IssuedCodeDTO;
import org.icedamericanomall.auth.app.dto.TokenGenerateParam;
import org.icedamericanomall.auth.app.dto.TokenPair;
import org.icedamericanomall.auth.app.port.CaptchaService;
import org.icedamericanomall.auth.app.port.LoginSecurityService;
import org.icedamericanomall.auth.app.port.LoginTokenService;
import org.icedamericanomall.auth.app.port.RiskControlService;
import org.icedamericanomall.auth.app.port.VerificationCodeIssueService;
import org.icedamericanomall.auth.domain.ability.auth.LoginStrategy;
import org.icedamericanomall.auth.domain.ability.auth.LoginStrategyFactory;
import org.icedamericanomall.auth.domain.ability.risk.RiskHandlerFactory;
import org.icedamericanomall.auth.domain.enums.CredentialTypeEnum;
import org.icedamericanomall.auth.domain.enums.PlatformEnum;
import org.icedamericanomall.auth.domain.enums.RiskLevel;
import org.icedamericanomall.auth.domain.enums.VerificationChannelEnum;
import org.icedamericanomall.auth.domain.enums.VerificationSceneEnum;
import org.icedamericanomall.auth.domain.gateway.UserAccountGateway;
import org.icedamericanomall.auth.domain.model.RiskAssessment;
import org.icedamericanomall.auth.domain.model.UserAuth;
import org.icedamericanomall.auth.domain.port.PasswordVerifier;
import org.icedamericanomall.auth.domain.port.VerificationCodeService;
import org.icedamericanomall.auth.domain.repository.UserAuthRepository;
import org.icedamericanomall.auth.domain.service.UserAuthProvisioner;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.enums.IdentityTypeEnum;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.common.utils.crypto.AesGcmUtil;
import org.icedamericanomall.common.utils.crypto.BlindIndexUtil;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * AuthManager 用例编排单元测试（R3 人机验证闭环 + 邮箱发码 + V5.2 注册/重置密码本地闭环）。
 *
 * <p>只验证编排：人机验证的四种状态（未携带 / 未启用 / 参数残缺 / 校验不通过）、
 * 邮箱发码的归一化与转发、注册（验证码 → 建档 → 凭证 → 签发）与重置密码
 * （验证码 → 盲索引定位 → 写新哈希）的编排顺序，业务规则本身在领域层测试。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthManager 单元测试")
class AuthManagerTest {

    @Mock
    private RiskHandlerFactory riskHandlerFactory;

    @Mock
    private LoginStrategyFactory strategyFactory;

    @Mock
    private UserAccountGateway userAccountGateway;

    @Mock
    private UserAuthProvisioner userAuthProvisioner;

    @Mock
    private UserAuthRepository userAuthRepository;

    @Mock
    private PasswordVerifier passwordVerifier;

    @Mock
    private LoginSecurityService loginSecurityService;

    @Mock
    private LoginTokenService loginTokenService;

    @Mock
    private RiskControlService riskControlService;

    @Mock
    private CaptchaService captchaService;

    @Mock
    private VerificationCodeService verificationCodeService;

    @Mock
    private VerificationCodeIssueService verificationCodeIssueService;

    @InjectMocks
    private AuthManager authManager;

    @BeforeAll
    static void initCrypto() {
        AesGcmUtil.init(Base64.getEncoder().encodeToString(new byte[32]));
        BlindIndexUtil.init(Base64.getEncoder().encodeToString(new byte[32]));
    }

    // ========== R3：人机验证闭环 ==========

    @Test
    @DisplayName("登录 — 携带验证参数且校验通过 → 继续登录流程")
    void shouldContinueLogin_whenCaptchaVerified() {
        stubLoginFlow();
        when(captchaService.isEnabled()).thenReturn(true);
        when(captchaService.verify(any(CaptchaVerifyParam.class))).thenReturn(true);

        assertNotNull(authManager.login(command(captchaParam())));

        verify(captchaService).verify(any(CaptchaVerifyParam.class));
        verify(strategyFactory).getStrategy(IdentityTypeEnum.USERNAME, CredentialTypeEnum.PASSWORD);
    }

    @Test
    @DisplayName("R3 闭环 — 人机验证通过后，风控处置不再重复要求验证（否则重试永远失败）")
    void shouldMarkCaptchaVerified_whenCaptchaPassed() {
        stubLoginFlow();
        when(captchaService.isEnabled()).thenReturn(true);
        when(captchaService.verify(any(CaptchaVerifyParam.class))).thenReturn(true);

        authManager.login(command(captchaParam()));

        ArgumentCaptor<RiskAssessment> captor = ArgumentCaptor.forClass(RiskAssessment.class);
        verify(riskHandlerFactory).handleRisk(captor.capture());
        assertTrue(captor.getValue().captchaVerified(), "已通过的人机验证必须透传给风控处置");
    }

    @Test
    @DisplayName("R3 闭环 — 未携带验证参数时，风控处置仍可要求验证（captchaVerified=false）")
    void shouldNotMarkCaptchaVerified_whenCaptchaAbsent() {
        stubLoginFlow();

        authManager.login(command(null));

        ArgumentCaptor<RiskAssessment> captor = ArgumentCaptor.forClass(RiskAssessment.class);
        verify(riskHandlerFactory).handleRisk(captor.capture());
        assertFalse(captor.getValue().captchaVerified());
    }

    @Test
    @DisplayName("登录 — 验证码错误 → 抛 CAPTCHA_ERROR，不进入风控与登录")
    void shouldRejectLogin_whenCaptchaFailed() {
        when(captchaService.isEnabled()).thenReturn(true);
        when(captchaService.verify(any(CaptchaVerifyParam.class))).thenReturn(false);

        BizException exception = assertThrows(BizException.class,
                () -> authManager.login(command(captchaParam())));

        assertEquals(ErrorCode.CAPTCHA_ERROR, exception.getErrorCode());
        verifyNoInteractions(riskControlService, strategyFactory, loginTokenService);
    }

    @Test
    @DisplayName("登录 — 未配置人机验证 → 跳过校验并放行（不得误拦截全部登录）")
    void shouldSkipCaptcha_whenNotConfigured() {
        stubLoginFlow();
        when(captchaService.isEnabled()).thenReturn(false);

        assertNotNull(authManager.login(command(captchaParam())));

        verify(captchaService, never()).verify(any(CaptchaVerifyParam.class));
        verify(strategyFactory).getStrategy(IdentityTypeEnum.USERNAME, CredentialTypeEnum.PASSWORD);
    }

    @Test
    @DisplayName("登录 — 验证参数残缺 → 参数错误，绝不当作未携带而放行")
    void shouldRejectLogin_whenCaptchaParamIncomplete() {
        when(captchaService.isEnabled()).thenReturn(true);
        CaptchaVerifyParam partial = new CaptchaVerifyParam(
                "GEETEST", "lot-1", null, null, null, "127.0.0.1");

        BizException exception = assertThrows(BizException.class,
                () -> authManager.login(command(partial)));

        assertEquals(ErrorCode.PARAM_ERROR, exception.getErrorCode());
        verify(captchaService, never()).verify(any(CaptchaVerifyParam.class));
        verifyNoInteractions(strategyFactory);
    }

    @Test
    @DisplayName("登录 — 未携带验证参数 → 不校验（风控命中时才由前端重试带回）")
    void shouldNotVerifyCaptcha_whenAbsent() {
        stubLoginFlow();

        assertNotNull(authManager.login(command(null)));

        verifyNoInteractions(captchaService);
        verify(strategyFactory).getStrategy(IdentityTypeEnum.USERNAME, CredentialTypeEnum.PASSWORD);
    }

    // ========== 邮箱验证码签发用例 ==========

    @Test
    @DisplayName("邮箱发码 — 邮箱归一化后转发校验服务，返回有效期")
    void shouldIssueEmailCode_withNormalizedEmail() {
        when(verificationCodeIssueService.issueCode(
                VerificationChannelEnum.EMAIL, "buyer@example.com", VerificationSceneEnum.LOGIN))
                .thenReturn(new IssuedCodeDTO(300));

        IssuedCodeDTO issued = authManager.sendEmailCode(EmailCodeCommand.builder()
                .email(" Buyer@Example.COM ")
                .scene(VerificationSceneEnum.LOGIN)
                .clientIp("127.0.0.1")
                .build());

        assertEquals(300, issued.expireSeconds());
        verify(verificationCodeIssueService).issueCode(
                VerificationChannelEnum.EMAIL, "buyer@example.com", VerificationSceneEnum.LOGIN);
    }

    @Test
    @DisplayName("邮箱发码 — 场景缺省为 LOGIN")
    void shouldDefaultSceneToLogin_whenAbsent() {
        when(verificationCodeIssueService.issueCode(
                VerificationChannelEnum.EMAIL, "buyer@example.com", VerificationSceneEnum.LOGIN))
                .thenReturn(new IssuedCodeDTO(300));

        authManager.sendEmailCode(EmailCodeCommand.builder().email("buyer@example.com").build());

        verify(verificationCodeIssueService).issueCode(
                VerificationChannelEnum.EMAIL, "buyer@example.com", VerificationSceneEnum.LOGIN);
    }

    @Test
    @DisplayName("邮箱发码 — 邮箱格式非法 → 参数错误，不打校验服务")
    void shouldRejectIssue_whenEmailInvalid() {
        BizException exception = assertThrows(BizException.class,
                () -> authManager.sendEmailCode(EmailCodeCommand.builder().email("bad-email").build()));

        assertEquals(ErrorCode.PARAM_ERROR, exception.getErrorCode());
        verifyNoInteractions(verificationCodeIssueService);
    }

    @Test
    @DisplayName("邮箱发码 — 校验服务拒绝（重发过频）→ 透传业务异常")
    void shouldPropagateException_whenIssueRejected() {
        when(verificationCodeIssueService.issueCode(
                VerificationChannelEnum.EMAIL, "buyer@example.com", VerificationSceneEnum.LOGIN))
                .thenThrow(new BizException(ErrorCode.FREQUENT_ERROR, "邮件发送过于频繁，请稍后再试"));

        BizException exception = assertThrows(BizException.class, () -> authManager.sendEmailCode(
                EmailCodeCommand.builder().email("buyer@example.com").build()));

        assertEquals(ErrorCode.FREQUENT_ERROR, exception.getErrorCode());
    }

    // ========== V5.2：注册 / 重置密码（认证边界收敛） ==========

    @Test
    @DisplayName("注册 — 验证码校验 → 远程建档 → 本地凭证建档 → 签发令牌（编排顺序）")
    void shouldRegister_withLocalCredential() {
        when(userAccountGateway.getOrCreateUserId("13800138000", IdentityTypeEnum.PHONE, "小明")).thenReturn(100L);
        when(passwordVerifier.encode("Aa123456!")).thenReturn("$2a$hash");
        when(userAuthProvisioner.findByHashOrCreateWithCredential(
                eq(IdentityTypeEnum.PHONE), anyString(), eq("13800138000"), eq("$2a$hash"), eq(null)))
                .thenReturn(UserAuth.builder().userId(100L).identityType(IdentityTypeEnum.PHONE).build());
        when(loginTokenService.createLoginResponse(any(TokenGenerateParam.class)))
                .thenReturn(new TokenPair(100L, "access", "refresh", 1800L));

        var loginDTO = authManager.register(org.icedamericanomall.auth.app.command.RegisterCommand.builder()
                .username("小明").phone("13800138000").password("Aa123456!").code("123456")
                .deviceId("dev-1").platform(PlatformEnum.WEB).build());

        assertEquals(100L, loginDTO.userId());
        // 验证码先消费（一次性），再建档，最后本地凭证带有正确盲索引
        verify(verificationCodeService).verifyAndConsume(VerificationChannelEnum.SMS, "13800138000", "123456");
        verify(userAccountGateway).getOrCreateUserId("13800138000", IdentityTypeEnum.PHONE, "小明");
        verify(userAuthProvisioner).findByHashOrCreateWithCredential(
                IdentityTypeEnum.PHONE, BlindIndexUtil.hmac("13800138000"), "13800138000", "$2a$hash", null);
    }

    @Test
    @DisplayName("注册 — 验证码校验失败 → 不建档不打远程（异常透传）")
    void shouldNotRegister_whenCodeRejected() {
        org.mockito.Mockito.doThrow(new BizException(ErrorCode.CAPTCHA_ERROR, "验证码错误"))
                .when(verificationCodeService)
                .verifyAndConsume(VerificationChannelEnum.SMS, "13800138000", "bad");

        assertThrows(BizException.class, () -> authManager.register(
                org.icedamericanomall.auth.app.command.RegisterCommand.builder()
                        .username("小明").phone("13800138000").password("Aa123456!").code("bad")
                        .platform(PlatformEnum.WEB).build()));

        verifyNoInteractions(userAccountGateway, userAuthProvisioner);
    }

    @Test
    @DisplayName("重置密码 — 验证码通过 → 盲索引定位凭证 → 写入新密码哈希（不调 user-service）")
    void shouldResetPassword_onLocalCredential() {
        UserAuth existing = UserAuth.builder().userId(100L).identityType(IdentityTypeEnum.PHONE)
                .credential("$2a$old").build();
        when(userAuthRepository.findByIdentityHash(IdentityTypeEnum.PHONE, BlindIndexUtil.hmac("13800138000")))
                .thenReturn(Optional.of(existing));
        when(passwordVerifier.encode("NewPw123!")).thenReturn("$2a$new");

        authManager.resetPassword(org.icedamericanomall.auth.app.command.ResetPasswordCommand.builder()
                .phone("13800138000").code("123456").newPassword("NewPw123!").build());

        verify(verificationCodeService).verifyAndConsume(VerificationChannelEnum.SMS, "13800138000", "123456");
        verify(userAuthRepository).updateByUserIdAndType(existing);
        assertEquals("$2a$new", existing.getCredential(), "凭证哈希已写入聚合");
    }

    @Test
    @DisplayName("重置密码 — 手机号未注册 → USER_NOT_FOUND（与历史语义一致）")
    void shouldRejectReset_whenPhoneNotRegistered() {
        when(userAuthRepository.findByIdentityHash(IdentityTypeEnum.PHONE, BlindIndexUtil.hmac("13900139000")))
                .thenReturn(Optional.empty());

        BizException exception = assertThrows(BizException.class,
                () -> authManager.resetPassword(org.icedamericanomall.auth.app.command.ResetPasswordCommand.builder()
                        .phone("13900139000").code("123456").newPassword("NewPw123!").build()));

        assertEquals(ErrorCode.USER_NOT_FOUND, exception.getErrorCode());
        verify(userAuthRepository, never()).updateByUserIdAndType(any());
    }

    // ========== 脚手架 ==========

    private void stubLoginFlow() {
        LoginStrategy strategy = org.mockito.Mockito.mock(LoginStrategy.class);
        when(strategy.login(any())).thenReturn(UserAuth.builder().userId(100L).build());
        lenient().when(strategyFactory.getStrategy(any(), any())).thenReturn(strategy);
        when(riskControlService.assess(any())).thenReturn(new RiskAssessment(RiskLevel.LOW, Set.of(), Map.of()));
        lenient().when(loginSecurityService.executeWithProtection(anyString(), any()))
                .thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(1)).get());
        lenient().when(loginTokenService.createLoginResponse(any(TokenGenerateParam.class)))
                .thenReturn(new TokenPair(100L, "access-token", "refresh-token", 1800L));
    }

    private LoginCommand command(CaptchaVerifyParam captchaVerify) {
        return LoginCommand.builder()
                .identityType(IdentityTypeEnum.USERNAME)
                .identifier("tom_1")
                .credentialType(CredentialTypeEnum.PASSWORD)
                .credential("Aa123456!")
                .platform(PlatformEnum.WEB)
                .clientIp("127.0.0.1")
                .captchaVerify(captchaVerify)
                .build();
    }

    private CaptchaVerifyParam captchaParam() {
        return new CaptchaVerifyParam("GEETEST", "lot-1", "output-1", "pass-1", "1700000000000", "127.0.0.1");
    }
}
