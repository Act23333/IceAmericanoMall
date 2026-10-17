package org.icedamericanomall.auth.domain.ability.auth.impl;

import org.icedamericanomall.auth.domain.enums.CredentialTypeEnum;
import org.icedamericanomall.auth.domain.enums.PlatformEnum;
import org.icedamericanomall.auth.domain.enums.UserAuthStatusEnum;
import org.icedamericanomall.auth.domain.model.LoginSpec;
import org.icedamericanomall.auth.domain.model.userauth.UserAuth;
import org.icedamericanomall.auth.domain.port.PasswordVerifier;
import org.icedamericanomall.auth.domain.repository.UserAuthRepository;
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
import org.springframework.http.HttpStatus;

import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PasswordLoginStrategy 单元测试 —— 密码校验、状态校验、敏感/非敏感标识查询分支，
 * 以及<b>登录失败口径一致性</b>与 <b>dummy verify（时序防护）</b>（2026-10-18 批次 ④）。
 *
 * <p><b>本类锁死的缺陷（修复前会失败）</b>：
 * <ol>
 *   <li>账号不存在 → {@code USER_NOT_FOUND(1102)} / HTTP <b>404</b>，密码错 → {@code PASSWORD_ERROR(3007)}
 *       / HTTP <b>200</b>，状态异常 → {@code USER_STATUS_ABNORMAL(3008)} / HTTP <b>200</b>；
 *       三者状态码/业务码/文案互不相同 ⇒ 攻击者不猜密码也能枚举账号。</li>
 *   <li>账号不存在时<b>直接抛异常</b>、不做任何哈希比对 ⇒ 响应时间比「密码错」快一个数量级
 *       （timing oracle），即使统一了状态码与文案仍可枚举。</li>
 * </ol>
 * 断言逐条对应这两点，且<b>只收紧不放宽</b>：原「账号不存在只断言文案」改为断言
 * 「状态码 + 业务码 + 文案 + dummy 比对实际发生」。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PasswordLoginStrategy 单元测试")
class PasswordLoginStrategyTest {

    private static final String HASHED = "$2a$10$abcdefghijklmnopqrstuv";

    /** 中性失败文案（对外唯一文案，不得区分原因） */
    private static final String NEUTRAL_MESSAGE = "账号或密码不正确";

    @Mock
    private UserAuthRepository userAuthRepository;

    @Mock
    private PasswordVerifier passwordVerifier;

    @InjectMocks
    private PasswordLoginStrategy strategy;

    @BeforeAll
    static void initCrypto() {
        // 盲索引工具需要密钥（单测用固定测试密钥）
        BlindIndexUtil.init(Base64.getEncoder().encodeToString(new byte[32]));
    }

    @Test
    @DisplayName("support — 密码凭证 + 用户名/手机号/邮箱")
    void shouldSupportPasswordCredential() {
        assertTrue(strategy.support(IdentityTypeEnum.USERNAME, CredentialTypeEnum.PASSWORD));
        assertTrue(strategy.support(IdentityTypeEnum.PHONE, CredentialTypeEnum.PASSWORD));
        assertTrue(strategy.support(IdentityTypeEnum.EMAIL, CredentialTypeEnum.PASSWORD));
        assertFalse(strategy.support(IdentityTypeEnum.WECHAT, CredentialTypeEnum.WECHAT_WEB_CODE));
    }

    @Test
    @DisplayName("用户名密码登录 — 密码正确 → 返回认证凭证")
    void shouldReturnUserAuth_whenPasswordMatches() {
        UserAuth userAuth = activeUserAuth();
        when(userAuthRepository.findByIdentity(IdentityTypeEnum.USERNAME, "tom_1"))
                .thenReturn(Optional.of(userAuth));
        when(passwordVerifier.matches("Aa123456!", HASHED)).thenReturn(true);

        UserAuth result = strategy.login(spec(IdentityTypeEnum.USERNAME, "tom_1", "Aa123456!"));

        assertSame(userAuth, result);
    }

    @Test
    @DisplayName("密码登录 — 密码错误 → 401 + 3007 + 中性文案（原断言只查异常类型，现查完整对外口径）")
    void shouldFailWithNeutralShape_whenPasswordMismatch() {
        UserAuth userAuth = activeUserAuth();
        when(userAuthRepository.findByIdentity(IdentityTypeEnum.USERNAME, "tom_1"))
                .thenReturn(Optional.of(userAuth));
        when(passwordVerifier.matches(anyString(), eq(HASHED))).thenReturn(false);

        BizException exception = assertThrows(BizException.class,
                () -> strategy.login(spec(IdentityTypeEnum.USERNAME, "tom_1", "wrong")));

        assertNeutralLoginFailure(exception);
        // 真实哈希比对：恰好一次，且不是对 dummy 哈希
        verify(passwordVerifier, times(1)).matches("wrong", HASHED);
    }

    @Test
    @DisplayName("手机号密码登录 — 走盲索引查询（不查明文）")
    void shouldQueryByBlindIndex_whenPhoneLogin() {
        UserAuth userAuth = activeUserAuth();
        when(userAuthRepository.findByIdentityHash(eq(IdentityTypeEnum.PHONE), anyString()))
                .thenReturn(Optional.of(userAuth));
        when(passwordVerifier.matches(anyString(), anyString())).thenReturn(true);

        UserAuth result = strategy.login(spec(IdentityTypeEnum.PHONE, "13888888888", "Aa123456!"));

        assertSame(userAuth, result);
        verify(userAuthRepository).findByIdentityHash(eq(IdentityTypeEnum.PHONE), anyString());
        verify(userAuthRepository, never()).findByIdentity(any(), anyString());
    }

    /**
     * 核心断言 1/n ——「账号不存在」不再返回 404，而是与「密码错误」完全同形。
     *
     * <p>修复前：{@code USER_NOT_FOUND / 404 / "账号或密码错误"}；修复后：
     * {@code PASSWORD_ERROR / 401 / "账号或密码不正确"}。
     */
    @Test
    @DisplayName("账号不存在 — 401 + 3007 + 中性文案（修复前是 1102 / 404，状态码可枚举）")
    void shouldFailWithNeutralShape_whenAccountNotFound() {
        when(userAuthRepository.findByIdentity(IdentityTypeEnum.USERNAME, "nobody"))
                .thenReturn(Optional.empty());

        BizException exception = assertThrows(BizException.class,
                () -> strategy.login(spec(IdentityTypeEnum.USERNAME, "nobody", "Aa123456!")));

        assertNeutralLoginFailure(exception);
    }

    /**
     * 核心断言 2/n ——「账号不存在时 dummy verify 被调用一次」。
     *
     * <p>修复前：账号不存在时 {@code passwordVerifier.matches} <b>零调用</b>（直接 404），
     * 响应时间比密码错误快一个数量级 ⇒ 本测试在修复前直接失败（verify 期望 1 次、实际 0 次）。
     */
    @Test
    @DisplayName("账号不存在 — 必须用预置的 dummy BCrypt 哈希做一次假比对（消除时间差）")
    void shouldPerformDummyVerify_whenAccountNotFound() {
        when(userAuthRepository.findByIdentity(IdentityTypeEnum.USERNAME, "nobody"))
                .thenReturn(Optional.empty());

        assertThrows(BizException.class,
                () -> strategy.login(spec(IdentityTypeEnum.USERNAME, "nobody", "Aa123456!")));

        // 恰好一次假比对，且比对的是预置哈希（不是任何库内哈希）
        verify(passwordVerifier, times(1)).matches("Aa123456!", PasswordLoginStrategy.DUMMY_PASSWORD_HASH);
    }

    /**
     * dummy verify 的第二个触发点：账号存在但从未设置密码（仅验证码/微信登录过的凭证行）。
     *
     * <p>此类行 {@code verifyPassword} 会因 credential 为空而提前返回 false —— 同样是「免费计时探针」，
     * 因此也必须消耗一次哈希成本。修复前这两条路径都不做任何比对。
     */
    @Test
    @DisplayName("账号存在但无密码凭证 — 同样做一次 dummy verify（否则无密码的账号更快被识别）")
    void shouldPerformDummyVerify_whenAccountHasNoPasswordCredential() {
        UserAuth smsOnly = activeUserAuth();
        smsOnly.setCredential(null);
        when(userAuthRepository.findByIdentity(IdentityTypeEnum.USERNAME, "tom_1"))
                .thenReturn(Optional.of(smsOnly));

        BizException exception = assertThrows(BizException.class,
                () -> strategy.login(spec(IdentityTypeEnum.USERNAME, "tom_1", "Aa123456!")));

        assertNeutralLoginFailure(exception);
        verify(passwordVerifier, times(1)).matches("Aa123456!", PasswordLoginStrategy.DUMMY_PASSWORD_HASH);
        // 无密码凭证 → 不得拿库内哈希去比对（此前是提前 return false，一次都不比对）
        verify(passwordVerifier, never()).matches(anyString(), eq(HASHED));
    }

    /**
     * 核心断言 3/n ——「账号状态不可登录」不再暴露 3008，与密码错完全一致。
     *
     * <p>修复前：{@code USER_STATUS_ABNORMAL(3008) / HTTP 200 / "账号状态异常"} ——
     * 与密码错的 {@code 3007 / 200} 业务码不同，前端/攻击者都能区分「这个账号存在且被冻结」。
     */
    @Test
    @DisplayName("账号已解绑 — 密码正确仍拒绝登录，且对外与密码错同形（原抛 3008 / 200）")
    void shouldFailWithNeutralShape_whenAccountNotActive() {
        UserAuth unbound = activeUserAuth();
        unbound.setStatus(UserAuthStatusEnum.UNBOUND);
        when(userAuthRepository.findByIdentity(IdentityTypeEnum.USERNAME, "tom_1"))
                .thenReturn(Optional.of(unbound));
        when(passwordVerifier.matches(anyString(), anyString())).thenReturn(true);

        BizException exception = assertThrows(BizException.class,
                () -> strategy.login(spec(IdentityTypeEnum.USERNAME, "tom_1", "Aa123456!")));

        assertNeutralLoginFailure(exception);
    }

    /**
     * 核心断言 4/n —— 三类失败<b>逐字段相等</b>（不是各自「都能抛异常」）。
     *
     * <p>这是「不得以状态码 / 业务码 / 文案区分失败原因」的机器可验证形式：
     * 任何一处口径漂移（例如有人把状态异常改回 3008）都会让本用例失败。
     */
    @Test
    @DisplayName("三类失败（账号不存在 / 密码错 / 状态不可登录）→ 同一 HTTP 状态码 + 同一业务码 + 同一文案")
    void shouldReturnIdenticalFailureShape_forAllLoginFailureCauses() {
        // 1) 账号不存在
        when(userAuthRepository.findByIdentity(IdentityTypeEnum.USERNAME, "nobody"))
                .thenReturn(Optional.empty());
        BizException notFound = assertThrows(BizException.class,
                () -> strategy.login(spec(IdentityTypeEnum.USERNAME, "nobody", "Aa123456!")));

        // 2) 密码错误
        UserAuth userAuth = activeUserAuth();
        when(userAuthRepository.findByIdentity(IdentityTypeEnum.USERNAME, "tom_1"))
                .thenReturn(Optional.of(userAuth));
        when(passwordVerifier.matches(anyString(), eq(HASHED))).thenReturn(false);
        BizException wrongPassword = assertThrows(BizException.class,
                () -> strategy.login(spec(IdentityTypeEnum.USERNAME, "tom_1", "wrong")));

        // 3) 状态不可登录（锁定的账号 + 正确密码）
        UserAuth locked = activeUserAuth();
        locked.setStatus(UserAuthStatusEnum.LOCKED);
        when(userAuthRepository.findByIdentity(IdentityTypeEnum.USERNAME, "tom_locked"))
                .thenReturn(Optional.of(locked));
        when(passwordVerifier.matches(anyString(), eq(HASHED))).thenReturn(true);
        BizException notActive = assertThrows(BizException.class,
                () -> strategy.login(spec(IdentityTypeEnum.USERNAME, "tom_locked", "Aa123456!")));

        for (BizException candidate : List.of(notFound, wrongPassword, notActive)) {
            assertNeutralLoginFailure(candidate);
        }
        // 三者的（HTTP 状态码, 业务码, 文案）三元组必须逐项相等
        assertSame(HttpStatus.UNAUTHORIZED, notFound.getErrorCode().getHttpStatus());
        assertEquals(notFound.getErrorCode().getCode(), wrongPassword.getErrorCode().getCode());
        assertEquals(notFound.getErrorCode().getCode(), notActive.getErrorCode().getCode());
        assertEquals(notFound.getDisplayMessage(), wrongPassword.getDisplayMessage());
        assertEquals(notFound.getDisplayMessage(), notActive.getDisplayMessage());
    }

    // ==================== 断言助手 ====================

    /** 断言「中性登录失败」：401 + 3007 + 统一文案 + 中性默认 message。 */
    private void assertNeutralLoginFailure(BizException exception) {
        assertEquals(ErrorCode.PASSWORD_ERROR, exception.getErrorCode(), "登录失败必须收敛到唯一业务码 3007");
        assertEquals(HttpStatus.UNAUTHORIZED, exception.getErrorCode().getHttpStatus(),
                "登录失败必须是 401（09-constraints §三·六），不得用 200/403/404 —— 状态码差异可枚举账号");
        assertEquals(NEUTRAL_MESSAGE, exception.getDisplayMessage(), "对外文案必须中性，不得区分失败原因");
        assertEquals(NEUTRAL_MESSAGE, ErrorCode.PASSWORD_ERROR.getMessage(),
                "ErrorCode 的默认文案也必须是中性文案（调用方漏传自定义文案时不得泄漏原因）");
    }

    private UserAuth activeUserAuth() {
        return UserAuth.builder()
                .id(1L)
                .userId(100L)
                .identityType(IdentityTypeEnum.USERNAME)
                .identifierPlain("tom_1")
                .credential(HASHED)
                .status(UserAuthStatusEnum.ACTIVE)
                .build();
    }

    private LoginSpec spec(IdentityTypeEnum identityType, String identifier, String credential) {
        return new LoginSpec(identityType, identifier, CredentialTypeEnum.PASSWORD, credential,
                PlatformEnum.WEB, "device-1", "127.0.0.1", "JUnit", Map.of());
    }
}
