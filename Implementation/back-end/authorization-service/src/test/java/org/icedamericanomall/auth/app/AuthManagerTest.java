package org.icedamericanomall.auth.app;

import org.icedamericanomall.auth.app.command.LoginCommand;
import org.icedamericanomall.auth.app.dto.CaptchaVerifyParam;
import org.icedamericanomall.auth.app.dto.TokenGenerateParam;
import org.icedamericanomall.auth.app.dto.TokenPair;
import org.icedamericanomall.auth.app.port.CaptchaService;
import org.icedamericanomall.auth.app.port.LoginSecurityService;
import org.icedamericanomall.auth.app.port.LoginTokenService;
import org.icedamericanomall.auth.app.port.RiskControlService;
import org.icedamericanomall.auth.app.port.TokenBlacklistService;
import org.icedamericanomall.auth.app.port.TokenVersionService;
import org.icedamericanomall.auth.domain.ability.auth.LoginStrategy;
import org.icedamericanomall.auth.domain.ability.auth.LoginStrategyFactory;
import org.icedamericanomall.auth.domain.ability.risk.RiskHandlerFactory;
import org.icedamericanomall.auth.app.rbac.SecurityContextManager;
import org.icedamericanomall.auth.domain.enums.CredentialTypeEnum;
import org.icedamericanomall.auth.domain.enums.PlatformEnum;
import org.icedamericanomall.auth.domain.enums.RiskLevel;
import org.icedamericanomall.auth.domain.enums.VerificationChannelEnum;
import org.icedamericanomall.auth.domain.enums.VerificationSceneEnum;
import org.icedamericanomall.auth.domain.gateway.UserAccountGateway;
import org.icedamericanomall.auth.domain.model.RiskAssessment;
import org.icedamericanomall.auth.domain.model.userauth.UserAuth;
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
 * <p>只验证编排：人机验证的状态（未携带 / 未启用 / 校验不通过 / 参数残缺 fail-closed）、
 * 注册（验证码 → 建档 → 凭证 → 签发）与重置密码（验证码 → 盲索引定位 → 写新哈希）
 * 的编排顺序，业务规则本身在领域层测试。
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
    private SecurityContextManager securityContextManager;

    @Mock
    private TokenBlacklistService tokenBlacklistService;

    @Mock
    private TokenVersionService tokenVersionService;

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
    @DisplayName("登录 — 验证参数残缺 → verification-service 校验失败（fail-closed），CAPTCHA_ERROR")
    void shouldRejectLogin_whenCaptchaParamIncomplete() {
        when(captchaService.isEnabled()).thenReturn(true);
        when(captchaService.verify(any(CaptchaVerifyParam.class))).thenReturn(false);
        CaptchaVerifyParam partial = new CaptchaVerifyParam(
                "GEETEST", Map.of("lotNumber", "lot-1"), "127.0.0.1");

        BizException exception = assertThrows(BizException.class,
                () -> authManager.login(command(partial)));

        assertEquals(ErrorCode.CAPTCHA_ERROR, exception.getErrorCode());
        verify(captchaService).verify(any(CaptchaVerifyParam.class));
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
        verify(verificationCodeService).verifyAndConsume(VerificationSceneEnum.REGISTER, VerificationChannelEnum.SMS, "13800138000", "123456");
        verify(userAccountGateway).getOrCreateUserId("13800138000", IdentityTypeEnum.PHONE, "小明");
        verify(userAuthProvisioner).findByHashOrCreateWithCredential(
                IdentityTypeEnum.PHONE, BlindIndexUtil.hmac("13800138000"), "13800138000", "$2a$hash", null);
    }

    @Test
    @DisplayName("注册 — 前置快拒绝：已注册（带密码凭证）手机号直接拒绝，不消费验证码、不建档（ADR-051）")
    void shouldFastRejectRegister_whenCredentialExists() {
        when(userAuthRepository.findByIdentityHash(IdentityTypeEnum.PHONE, BlindIndexUtil.hmac("13800138000")))
                .thenReturn(Optional.of(UserAuth.builder().userId(100L).identityType(IdentityTypeEnum.PHONE)
                        .credential("$2a$hash").build()));

        BizException exception = assertThrows(BizException.class, () -> authManager.register(
                org.icedamericanomall.auth.app.command.RegisterCommand.builder()
                        .username("小明").phone("13800138000").password("Aa123456!").code("123456")
                        .platform(PlatformEnum.WEB).build()));

        assertEquals(ErrorCode.PHONE_ALREADY_REGISTERED, exception.getErrorCode());
        verifyNoInteractions(verificationCodeService, userAccountGateway, userAuthProvisioner);
    }

    @Test
    @DisplayName("注册 — 验证码校验失败 → 不建档不打远程（异常透传）")
    void shouldNotRegister_whenCodeRejected() {
        org.mockito.Mockito.doThrow(new BizException(ErrorCode.CAPTCHA_ERROR, "验证码错误"))
                .when(verificationCodeService)
                .verifyAndConsume(VerificationSceneEnum.REGISTER, VerificationChannelEnum.SMS, "13800138000", "bad");

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

        verify(verificationCodeService).verifyAndConsume(VerificationSceneEnum.RESET_PASSWORD, VerificationChannelEnum.SMS, "13800138000", "123456");
        verify(userAuthRepository).updateByUserIdAndType(existing);
        assertEquals("$2a$new", existing.getCredential(), "凭证哈希已写入聚合");
    }

    @Test
    @DisplayName("重置密码 — 成功后用户级吊销：推进令牌版本 + 拆除该用户全部 refresh 会话（ADR-031）")
    void shouldRevokeUserTokens_whenResetPassword() {
        UserAuth existing = UserAuth.builder().userId(100L).identityType(IdentityTypeEnum.PHONE)
                .credential("$2a$old").build();
        when(userAuthRepository.findByIdentityHash(IdentityTypeEnum.PHONE, BlindIndexUtil.hmac("13800138000")))
                .thenReturn(Optional.of(existing));
        when(passwordVerifier.encode("NewPw123!")).thenReturn("$2a$new");

        authManager.resetPassword(org.icedamericanomall.auth.app.command.ResetPasswordCommand.builder()
                .phone("13800138000").code("123456").newPassword("NewPw123!").build());

        verify(tokenVersionService).incrementVersion(100L);
        verify(loginTokenService).revokeAllSessions(100L);
        verify(tokenBlacklistService, never()).blacklist(any());
    }

    @Test
    @DisplayName("重置密码 — 匿名改密同样触发用户级吊销（userId 取自凭证行，不依赖请求令牌）")
    void shouldRevokeUserTokens_whenResetPasswordAnonymous() {
        UserAuth existing = UserAuth.builder().userId(100L).identityType(IdentityTypeEnum.PHONE)
                .credential("$2a$old").build();
        when(userAuthRepository.findByIdentityHash(IdentityTypeEnum.PHONE, BlindIndexUtil.hmac("13800138000")))
                .thenReturn(Optional.of(existing));
        when(passwordVerifier.encode("NewPw123!")).thenReturn("$2a$new");

        authManager.resetPassword(org.icedamericanomall.auth.app.command.ResetPasswordCommand.builder()
                .phone("13800138000").code("123456").newPassword("NewPw123!").build());

        verify(tokenVersionService).incrementVersion(100L);
        verify(loginTokenService).revokeAllSessions(100L);
    }

    @Test
    @DisplayName("重置密码 — 手机号未注册 → USER_NOT_FOUND（与历史语义一致），不吊销任何令牌")
    void shouldRejectReset_whenPhoneNotRegistered() {
        when(userAuthRepository.findByIdentityHash(IdentityTypeEnum.PHONE, BlindIndexUtil.hmac("13900139000")))
                .thenReturn(Optional.empty());

        BizException exception = assertThrows(BizException.class,
                () -> authManager.resetPassword(org.icedamericanomall.auth.app.command.ResetPasswordCommand.builder()
                        .phone("13900139000").code("123456").newPassword("NewPw123!").build()));

        assertEquals(ErrorCode.USER_NOT_FOUND, exception.getErrorCode());
        verify(userAuthRepository, never()).updateByUserIdAndType(any());
        verifyNoInteractions(tokenVersionService, tokenBlacklistService);
    }

    // ========== ADR-031：内部端点用户级吊销 ==========

    @Test
    @DisplayName("内部吊销 — 推进令牌版本 + 拆除全部 refresh 会话（管理端禁用即时下线）")
    void shouldRevokeVersionAndSessions_whenInternalRevokeCalled() {
        when(tokenVersionService.incrementVersion(7L)).thenReturn(3L);

        authManager.revokeUserTokens(7L);

        verify(tokenVersionService).incrementVersion(7L);
        verify(loginTokenService).revokeAllSessions(7L);
    }

    @Test
    @DisplayName("内部吊销 — 无 refresh 会话可拆时依旧推进版本（两步动作独立，互不短路）")
    void shouldStillIncrementVersion_whenNoSessionsToRevoke() {
        authManager.revokeUserTokens(9L);

        verify(tokenVersionService).incrementVersion(9L);
        verify(loginTokenService).revokeAllSessions(9L);
        verifyNoInteractions(tokenBlacklistService);
    }

    // ========== ADR-027：登出拉黑 access token ==========

    @Test
    @DisplayName("登出 — 吊销 refresh 会话 + 拉黑当前 access token（Bearer 前缀已剥离）")
    void shouldRevokeSessionAndBlacklistToken_whenLogout() {
        authManager.logout("refresh-token-1", "Bearer access-token-1");

        verify(loginTokenService).logout("refresh-token-1");
        verify(tokenBlacklistService).blacklist("access-token-1");
    }

    @Test
    @DisplayName("登出 — 未携带 Authorization → 仅吊销 refresh 会话，拉黑传 null（实现内跳过）")
    void shouldOnlyRevokeSession_whenLogoutWithoutAuthorization() {
        authManager.logout("refresh-token-1", null);

        verify(loginTokenService).logout("refresh-token-1");
        verify(tokenBlacklistService).blacklist(null);
    }

    @Test
    @DisplayName("登出 — Authorization 非 Bearer 前缀 → 视为无令牌（传 null，不误把 Basic 值当令牌拉黑）")
    void shouldNotStripPrefix_whenAuthorizationNotBearer() {
        authManager.logout("refresh-token-1", "Basic abc");

        verify(tokenBlacklistService).blacklist(null);
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
        return new CaptchaVerifyParam("GEETEST", Map.of(
                "lotNumber", "lot-1",
                "captchaOutput", "output-1",
                "passToken", "pass-1",
                "genTime", "1700000000000"), "127.0.0.1");
    }
}
