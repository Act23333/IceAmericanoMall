package org.icedamericanomall.auth.login;

import org.icedamericanomall.auth.domain.ability.auth.impl.PasswordLoginStrategy;
import org.icedamericanomall.auth.domain.enums.CredentialTypeEnum;
import org.icedamericanomall.auth.domain.enums.PlatformEnum;
import org.icedamericanomall.auth.domain.enums.UserAuthStatusEnum;
import org.icedamericanomall.auth.domain.model.LoginSpec;
import org.icedamericanomall.auth.domain.model.UserAuth;
import org.icedamericanomall.auth.domain.port.PasswordVerifier;
import org.icedamericanomall.auth.domain.repository.UserAuthRepository;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PasswordLoginStrategy 单元测试 —— 密码校验、状态校验、敏感/非敏感标识查询分支。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PasswordLoginStrategy 单元测试")
class PasswordLoginStrategyTest {

    private static final String HASHED = "$2a$10$abcdefghijklmnopqrstuv";

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
    @DisplayName("密码登录 — 密码错误 → 抛出业务异常且不签发")
    void shouldThrow_whenPasswordMismatch() {
        UserAuth userAuth = activeUserAuth();
        when(userAuthRepository.findByIdentity(IdentityTypeEnum.USERNAME, "tom_1"))
                .thenReturn(Optional.of(userAuth));
        when(passwordVerifier.matches(anyString(), eq(HASHED))).thenReturn(false);

        assertThrows(BizException.class, () -> strategy.login(spec(IdentityTypeEnum.USERNAME, "tom_1", "wrong")));
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

    @Test
    @DisplayName("账号不存在 — 提示与密码错误一致（防账号枚举）")
    void shouldThrow_whenAccountNotFound() {
        when(userAuthRepository.findByIdentity(IdentityTypeEnum.USERNAME, "nobody"))
                .thenReturn(Optional.empty());

        BizException exception = assertThrows(BizException.class,
                () -> strategy.login(spec(IdentityTypeEnum.USERNAME, "nobody", "Aa123456!")));

        assertEquals("账号或密码错误", exception.getDisplayMessage());
    }

    @Test
    @DisplayName("账号已解绑 — 密码正确仍拒绝登录")
    void shouldThrow_whenAccountNotActive() {
        UserAuth unbound = activeUserAuth();
        unbound.setStatus(UserAuthStatusEnum.UNBOUND);
        when(userAuthRepository.findByIdentity(IdentityTypeEnum.USERNAME, "tom_1"))
                .thenReturn(Optional.of(unbound));
        when(passwordVerifier.matches(anyString(), anyString())).thenReturn(true);

        assertThrows(BizException.class, () -> strategy.login(spec(IdentityTypeEnum.USERNAME, "tom_1", "Aa123456!")));
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
