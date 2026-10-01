package org.icedamericanomall.auth.app;

import org.icedamericanomall.auth.app.command.RegisterCommand;
import org.icedamericanomall.auth.app.dto.LoginDTO;
import org.icedamericanomall.auth.app.dto.TokenGenerateParam;
import org.icedamericanomall.auth.app.dto.TokenPair;
import org.icedamericanomall.auth.app.port.CaptchaService;
import org.icedamericanomall.auth.app.port.LoginSecurityService;
import org.icedamericanomall.auth.app.port.LoginTokenService;
import org.icedamericanomall.auth.app.port.RiskControlService;
import org.icedamericanomall.auth.app.port.VerificationCodeIssueService;
import org.icedamericanomall.auth.domain.ability.auth.LoginStrategyFactory;
import org.icedamericanomall.auth.domain.ability.auth.impl.PasswordLoginStrategy;
import org.icedamericanomall.auth.domain.ability.risk.RiskHandlerFactory;
import org.icedamericanomall.auth.domain.enums.CredentialTypeEnum;
import org.icedamericanomall.auth.domain.enums.PlatformEnum;
import org.icedamericanomall.auth.domain.enums.UserAuthStatusEnum;
import org.icedamericanomall.auth.domain.enums.VerificationChannelEnum;
import org.icedamericanomall.auth.domain.exception.DuplicateIdentityException;
import org.icedamericanomall.auth.domain.gateway.UserAccountGateway;
import org.icedamericanomall.auth.domain.model.LoginSpec;
import org.icedamericanomall.auth.domain.model.UserAuth;
import org.icedamericanomall.auth.domain.port.DistributedLock;
import org.icedamericanomall.auth.domain.port.PasswordVerifier;
import org.icedamericanomall.auth.domain.port.VerificationCodeService;
import org.icedamericanomall.auth.domain.repository.UserAuthRepository;
import org.icedamericanomall.auth.domain.service.UserAuthProvisioner;
import org.icedamericanomall.auth.infrastructure.adapter.PasswordVerifierAdapter;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.enums.IdentityTypeEnum;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.common.model.EncryptedIdentifier;
import org.icedamericanomall.common.utils.crypto.AesGcmUtil;
import org.icedamericanomall.common.utils.crypto.BlindIndexUtil;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 注册 → 密码登录 边界收敛测试（V5.2 凭证脑裂修复的证据测试，ADR-017）。
 *
 * <p><b>修复前的缺陷</b>：注册链路只写 user-service 的 {@code user.password}，
 * authorization-service 本地不落 {@code user_auth} 凭证行 → 密码登录策略查本地凭证
 * 必然"账号或密码错误"（凭证脑裂）。
 *
 * <p><b>本测试</b>：mock 掉远程建档网关（{@code UserAccountGateway}），
 * 使用<b>真实的</b> {@code UserAuthProvisioner}（含互斥锁端口）与真实的
 * {@code PasswordLoginStrategy}（BCrypt 哈希比较），以内存仓储贯穿"注册 → 密码登录"全链路，
 * 断言注册写入的凭证行可以被密码登录直接校验通过；并覆盖防覆盖（重复注册拒绝）语义。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("注册→密码登录 边界收敛测试（凭证脑裂修复）")
class RegisterThenPasswordLoginTest {

    private static final String PHONE = "13800138000";
    private static final String PASSWORD = "Aa123456!";
    private static final Long USER_ID = 100L;

    @Mock
    private UserAccountGateway userAccountGateway;

    @Mock
    private VerificationCodeService verificationCodeService;

    @Mock
    private LoginTokenService loginTokenService;

    private InMemoryUserAuthRepository userAuthRepository;
    private PasswordVerifier passwordVerifier;
    private UserAuthProvisioner provisioner;
    private AuthManager authManager;

    @BeforeAll
    static void initCrypto() {
        AesGcmUtil.init(Base64.getEncoder().encodeToString(new byte[32]));
        BlindIndexUtil.init(Base64.getEncoder().encodeToString(new byte[32]));
    }

    @BeforeEach
    void setUp() {
        userAuthRepository = new InMemoryUserAuthRepository();
        passwordVerifier = new PasswordVerifierAdapter(new BCryptPasswordEncoder());

        DistributedLock passthroughLock = mock(DistributedLock.class);
        lenient().when(passthroughLock.executeWithLock(anyString(), any()))
                .thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(1)).get());
        provisioner = new UserAuthProvisioner(userAuthRepository, userAccountGateway, passthroughLock);

        authManager = new AuthManager(
                mock(RiskHandlerFactory.class),
                mock(LoginStrategyFactory.class),
                userAccountGateway,
                provisioner,
                userAuthRepository,
                passwordVerifier,
                mock(LoginSecurityService.class),
                loginTokenService,
                mock(RiskControlService.class),
                mock(CaptchaService.class),
                verificationCodeService,
                mock(VerificationCodeIssueService.class));
    }

    @Test
    @DisplayName("脑裂修复 — 注册后密码登录成功（注册写入的 user_auth 凭证可被密码登录校验）")
    void shouldLoginWithPassword_afterRegister() {
        stubProvisioningAndToken();

        LoginDTO registered = authManager.register(registerCommand());

        // 注册本地已落凭证行（credential = BCrypt 哈希），并签发 token
        assertEquals(USER_ID, registered.userId());
        UserAuth stored = userAuthRepository
                .findByIdentityHash(IdentityTypeEnum.PHONE, BlindIndexUtil.hmac(PHONE))
                .orElseThrow();
        assertNotNull(stored.getCredential(), "注册必须在 user_auth 落密码凭证（脑裂修复点）");

        // 用真实密码登录策略验证：注册后密码登录必须成功
        PasswordLoginStrategy loginStrategy = new PasswordLoginStrategy(userAuthRepository, passwordVerifier);
        UserAuth loggedIn = loginStrategy.login(passwordSpec(PHONE, PASSWORD));

        assertEquals(USER_ID, loggedIn.getUserId());
        assertEquals(IdentityTypeEnum.PHONE, loggedIn.getIdentityType());
        // 验证码已在注册用例中消费（一次性）
        verify(verificationCodeService).verifyAndConsume(VerificationChannelEnum.SMS, PHONE, "123456");
    }

    @Test
    @DisplayName("脑裂修复 — 密码错误仍被拒绝（凭证校验真实生效，不是无条件放行）")
    void shouldRejectWrongPassword_afterRegister() {
        stubProvisioningAndToken();
        authManager.register(registerCommand());

        PasswordLoginStrategy loginStrategy = new PasswordLoginStrategy(userAuthRepository, passwordVerifier);
        BizException exception = assertThrows(BizException.class,
                () -> loginStrategy.login(passwordSpec(PHONE, "wrong-password")));

        assertEquals(ErrorCode.PASSWORD_ERROR, exception.getErrorCode());
    }

    @Test
    @DisplayName("防账号接管 — 已注册手机号再次注册被拒绝（不覆盖既有凭证）")
    void shouldRejectSecondRegister_whenAlreadyCredentialed() {
        stubProvisioningAndToken();
        authManager.register(registerCommand());

        BizException exception = assertThrows(BizException.class,
                () -> authManager.register(registerCommand()));

        assertEquals(ErrorCode.PHONE_ALREADY_REGISTERED, exception.getErrorCode());
        // 既有凭证未被覆盖：原密码仍可登录
        PasswordLoginStrategy loginStrategy = new PasswordLoginStrategy(userAuthRepository, passwordVerifier);
        assertEquals(USER_ID, loginStrategy.login(passwordSpec(PHONE, PASSWORD)).getUserId());
    }

    @Test
    @DisplayName("验证码先行 — 验证码校验失败时不得建档（异常向上透传）")
    void shouldNotProvision_whenCodeRejected() {
        org.mockito.Mockito.doThrow(new BizException(ErrorCode.CAPTCHA_ERROR, "验证码错误"))
                .when(verificationCodeService)
                .verifyAndConsume(VerificationChannelEnum.SMS, PHONE, "000001");

        RegisterCommand bad = RegisterCommand.builder()
                .username("小明").phone(PHONE).password(PASSWORD).code("000001")
                .deviceId("dev-1").platform(PlatformEnum.WEB).build();

        assertThrows(BizException.class, () -> authManager.register(bad));
        assertEquals(0, userAuthRepository.size(), "验证码未通过不得建档");
    }

    @Test
    @DisplayName("先短信登录后注册 — 补写密码凭证后密码登录可用（已有行补 credential）")
    void shouldAttachCredential_whenRowCreatedBySmsLoginFirst() {
        stubProvisioningAndToken();
        // 模拟"短信登录先创建了无凭证行"（同手机号盲索引）
        UserAuth smsCreated = UserAuth.builder()
                .userId(USER_ID)
                .identityType(IdentityTypeEnum.PHONE)
                .identifier(EncryptedIdentifier.fromPlain(PHONE))
                .lastLoginTime(java.time.LocalDateTime.now())
                .build();
        userAuthRepository.insert(smsCreated);

        authManager.register(registerCommand());

        PasswordLoginStrategy loginStrategy = new PasswordLoginStrategy(userAuthRepository, passwordVerifier);
        assertEquals(USER_ID, loginStrategy.login(passwordSpec(PHONE, PASSWORD)).getUserId());
        assertEquals(1, userAuthRepository.size(), "不得重复建档");
    }

    // ==================== 脚手架 ====================

    private void stubProvisioningAndToken() {
        // 注册用例显式建档（带用户名）与凭证建档内部回查（不带昵称）共用同一幂等网关
        when(userAccountGateway.getOrCreateUserId(eq(PHONE), eq(IdentityTypeEnum.PHONE), any())).thenReturn(USER_ID);
        when(loginTokenService.createLoginResponse(any(TokenGenerateParam.class)))
                .thenReturn(new TokenPair(USER_ID, "access-token", "refresh-token", 1800L));
    }

    private RegisterCommand registerCommand() {
        return RegisterCommand.builder()
                .username("小明")
                .phone(PHONE)
                .password(PASSWORD)
                .code("123456")
                .deviceId("dev-1")
                .platform(PlatformEnum.WEB)
                .build();
    }

    private LoginSpec passwordSpec(String phone, String password) {
        return new LoginSpec(IdentityTypeEnum.PHONE, phone, CredentialTypeEnum.PASSWORD, password,
                PlatformEnum.WEB, "dev-1", "127.0.0.1", "JUnit", java.util.Map.of());
    }

    /**
     * 内存凭证仓储（测试替身）—— 只实现本链路用到的按业务键操作，
     * 行为对齐 {@code UserAuthRepositoryImpl}（盲索引/明文唯一、状态过滤）。
     */
    private static class InMemoryUserAuthRepository implements UserAuthRepository {

        private final List<UserAuth> rows = new ArrayList<>();
        private long sequence = 1L;

        @Override
        public UserAuth insert(UserAuth userAuth) {
            boolean conflicted = rows.stream().anyMatch(row ->
                    sameType(row, userAuth) && (sameHash(row, userAuth) || samePlain(row, userAuth)));
            if (conflicted) {
                throw new DuplicateIdentityException(ErrorCode.PARAM_ERROR, "认证标识已存在");
            }
            userAuth.setId(sequence++);
            rows.add(userAuth);
            return userAuth;
        }

        @Override
        public int updateByUserIdAndType(UserAuth userAuth) {
            return rows.stream()
                    .filter(row -> sameType(row, userAuth)
                            && java.util.Objects.equals(row.getUserId(), userAuth.getUserId()))
                    .findFirst()
                    .map(row -> {
                        row.setCredential(userAuth.getCredential());
                        row.setLastLoginTime(userAuth.getLastLoginTime());
                        return 1;
                    })
                    .orElse(0);
        }

        @Override
        public UserAuth upsert(UserAuth userAuth) {
            updateByUserIdAndType(userAuth);
            return userAuth;
        }

        @Override
        public Optional<UserAuth> findByIdentity(IdentityTypeEnum type, String identifierPlain) {
            return rows.stream()
                    .filter(row -> sameType(row, type) && identifierPlain != null
                            && identifierPlain.equals(row.getIdentifierPlain()))
                    .findFirst();
        }

        @Override
        public Optional<UserAuth> findByIdentityHash(IdentityTypeEnum type, String identifierHash) {
            return rows.stream()
                    .filter(row -> sameType(row, type) && row.getIdentifier() != null
                            && identifierHash != null && identifierHash.equals(row.getIdentifier().getHash()))
                    .findFirst();
        }

        @Override
        public Optional<UserAuth> findFirstByUnionId(String unionId) {
            return rows.stream()
                    .filter(row -> unionId != null && unionId.equals(row.getUnionId()))
                    .findFirst();
        }

        @Override
        public List<UserAuth> findByUserId(Long userId) {
            return rows.stream().filter(row -> java.util.Objects.equals(row.getUserId(), userId)).toList();
        }

        @Override
        public Optional<UserAuth> findByUserIdAndType(Long userId, IdentityTypeEnum type) {
            return rows.stream()
                    .filter(row -> sameType(row, type) && java.util.Objects.equals(row.getUserId(), userId))
                    .findFirst();
        }

        int size() {
            return rows.size();
        }

        private static boolean sameType(UserAuth row, UserAuth other) {
            return row.getIdentityType() == other.getIdentityType()
                    && row.getStatus() == UserAuthStatusEnum.ACTIVE;
        }

        private static boolean sameType(UserAuth row, IdentityTypeEnum type) {
            return row.getIdentityType() == type;
        }

        private static boolean sameHash(UserAuth row, UserAuth other) {
            return row.getIdentifier() != null && other.getIdentifier() != null
                    && row.getIdentifier().getHash().equals(other.getIdentifier().getHash());
        }

        private static boolean samePlain(UserAuth row, UserAuth other) {
            return row.getIdentifierPlain() != null && row.getIdentifierPlain().equals(other.getIdentifierPlain());
        }
    }
}
