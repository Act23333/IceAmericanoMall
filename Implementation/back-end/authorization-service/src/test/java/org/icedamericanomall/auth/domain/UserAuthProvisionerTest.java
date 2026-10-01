package org.icedamericanomall.auth.domain;

import org.icedamericanomall.auth.domain.exception.DuplicateIdentityException;
import org.icedamericanomall.auth.domain.gateway.UserAccountGateway;
import org.icedamericanomall.auth.domain.model.UserAuth;
import org.icedamericanomall.auth.domain.port.DistributedLock;
import org.icedamericanomall.auth.domain.repository.UserAuthRepository;
import org.icedamericanomall.auth.domain.service.UserAuthProvisioner;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.enums.IdentityTypeEnum;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.common.exception.DBException;
import org.icedamericanomall.common.utils.crypto.AesGcmUtil;
import org.icedamericanomall.common.utils.crypto.BlindIndexUtil;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Base64;
import java.util.Optional;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * UserAuthProvisioner 领域服务单元测试 —— 查找优先、加锁创建、并发冲突回查；
 * V5.2 增补：注册链路带凭证建档（凭证唯一所有权，ADR-017）。
 *
 * <p>纯单元测试：不依赖 Spring 容器，锁与远程能力均为 Mock。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserAuthProvisioner 领域服务单元测试")
class UserAuthProvisionerTest {

    private static final String IDENTIFIER_HASH = "hash-of-13888888888";

    @Mock
    private UserAuthRepository userAuthRepository;

    @Mock
    private UserAccountGateway userAccountGateway;

    @Mock
    private DistributedLock distributedLock;

    private UserAuthProvisioner provisioner;

    @BeforeAll
    static void initCrypto() {
        AesGcmUtil.init(Base64.getEncoder().encodeToString(new byte[32]));
        BlindIndexUtil.init(Base64.getEncoder().encodeToString(new byte[32]));
    }

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        provisioner = new UserAuthProvisioner(userAuthRepository, userAccountGateway, distributedLock);
        // Mock 锁：直接执行临界区（真实实现为 Redisson 互斥）
        lenient().when(distributedLock.executeWithLock(anyString(), any()))
                .thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(1)).get());
    }

    @Test
    @DisplayName("敏感标识 — 已存在 → 直接返回，不创建、不加锁")
    void shouldReturnExisting_whenHashMatched() {
        UserAuth existing = UserAuth.builder().userId(100L).identityType(IdentityTypeEnum.PHONE).build();
        when(userAuthRepository.findByIdentityHash(IdentityTypeEnum.PHONE, IDENTIFIER_HASH))
                .thenReturn(Optional.of(existing));

        UserAuth result = provisioner.findByHashOrCreate(IdentityTypeEnum.PHONE, IDENTIFIER_HASH, "13888888888");

        assertSame(existing, result);
        verify(distributedLock, never()).executeWithLock(anyString(), any());
        verify(userAuthRepository, never()).insert(any());
    }

    @Test
    @DisplayName("敏感标识 — 不存在 → 取用户 ID 并创建凭证（含三字段加密）")
    void shouldCreate_whenHashNotFound() {
        when(userAuthRepository.findByIdentityHash(IdentityTypeEnum.PHONE, IDENTIFIER_HASH))
                .thenReturn(Optional.empty());
        when(userAccountGateway.getOrCreateUserId("13888888888", IdentityTypeEnum.PHONE, null)).thenReturn(100L);
        when(userAuthRepository.insert(any(UserAuth.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        UserAuth result = provisioner.findByHashOrCreate(IdentityTypeEnum.PHONE, IDENTIFIER_HASH, "13888888888");

        assertEquals(100L, result.getUserId());
        assertEquals(IdentityTypeEnum.PHONE, result.getIdentityType());
        // 三字段模型：HMAC-SHA256 盲索引（64 位十六进制）与脱敏值必须落库
        assertEquals(64, result.getIdentifier().getHash().length());
        assertEquals("138****8888", result.getIdentifier().getMask());
        verify(distributedLock).executeWithLock(anyString(), any());
    }

    @Test
    @DisplayName("敏感标识 — 并发创建撞唯一索引 → 回查既有记录返回（幂等）")
    void shouldReload_whenDuplicateIdentity() {
        UserAuth existing = UserAuth.builder().userId(100L).identityType(IdentityTypeEnum.PHONE).build();
        // 锁前查空 → 锁内查空 → 冲突后回查命中
        when(userAuthRepository.findByIdentityHash(IdentityTypeEnum.PHONE, IDENTIFIER_HASH))
                .thenReturn(Optional.empty(), Optional.empty(), Optional.of(existing));
        when(userAccountGateway.getOrCreateUserId("13888888888", IdentityTypeEnum.PHONE, null)).thenReturn(100L);
        when(userAuthRepository.insert(any(UserAuth.class)))
                .thenThrow(new DuplicateIdentityException(ErrorCode.PARAM_ERROR, "认证标识已存在"));

        UserAuth result = provisioner.findByHashOrCreate(IdentityTypeEnum.PHONE, IDENTIFIER_HASH, "13888888888");

        assertSame(existing, result);
    }

    @Test
    @DisplayName("敏感标识 — 撞索引且回查为空 → 抛出 DBException（不吞异常）")
    void shouldThrowDbException_whenDuplicateAndReloadEmpty() {
        when(userAuthRepository.findByIdentityHash(IdentityTypeEnum.PHONE, IDENTIFIER_HASH))
                .thenReturn(Optional.empty());
        when(userAccountGateway.getOrCreateUserId("13888888888", IdentityTypeEnum.PHONE, null)).thenReturn(100L);
        when(userAuthRepository.insert(any(UserAuth.class)))
                .thenThrow(new DuplicateIdentityException(ErrorCode.PARAM_ERROR, "认证标识已存在"));

        assertThrows(DBException.class,
                () -> provisioner.findByHashOrCreate(IdentityTypeEnum.PHONE, IDENTIFIER_HASH, "13888888888"));
    }

    // ==================== V5.2：注册链路带凭证建档 ====================

    @Test
    @DisplayName("注册 — 凭证不存在 → 建档并写入 passwordVerifier 哈希（credential 落库）")
    void shouldCreateWithCredential_whenAbsent() {
        when(userAuthRepository.findByIdentityHash(IdentityTypeEnum.PHONE, IDENTIFIER_HASH))
                .thenReturn(Optional.empty());
        when(userAccountGateway.getOrCreateUserId("13888888888", IdentityTypeEnum.PHONE, "小明")).thenReturn(100L);
        when(userAuthRepository.insert(any(UserAuth.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        UserAuth result = provisioner.findByHashOrCreateWithCredential(
                IdentityTypeEnum.PHONE, IDENTIFIER_HASH, "13888888888", "$2a$bcrypt-hash", "小明");

        assertEquals(100L, result.getUserId());
        assertEquals("$2a$bcrypt-hash", result.getCredential(), "注册必须在 user_auth 落密码凭证");
        assertEquals("138****8888", result.getIdentifier().getMask());
    }

    @Test
    @DisplayName("注册 — 已存在无凭证行（先短信登录）→ 补齐密码凭证（幂等，不重复建档）")
    void shouldAttachCredential_whenExistingWithoutCredential() {
        UserAuth existing = UserAuth.builder()
                .userId(100L).identityType(IdentityTypeEnum.PHONE).build();
        when(userAuthRepository.findByIdentityHash(IdentityTypeEnum.PHONE, IDENTIFIER_HASH))
                .thenReturn(Optional.of(existing));

        UserAuth result = provisioner.findByHashOrCreateWithCredential(
                IdentityTypeEnum.PHONE, IDENTIFIER_HASH, "13888888888", "$2a$bcrypt-hash", null);

        assertEquals("$2a$bcrypt-hash", result.getCredential());
        verify(userAuthRepository).updateByUserIdAndType(existing);
        verify(userAccountGateway, never()).getOrCreateUserId(anyString(), any(), any());
    }

    @Test
    @DisplayName("注册 — 标识已有凭证（已注册）→ 拒绝覆盖（PHONE_ALREADY_REGISTERED，防账号接管）")
    void shouldReject_whenAlreadyCredentialed() {
        UserAuth existing = UserAuth.builder()
                .userId(100L).identityType(IdentityTypeEnum.PHONE).credential("$2a$existing").build();
        when(userAuthRepository.findByIdentityHash(IdentityTypeEnum.PHONE, IDENTIFIER_HASH))
                .thenReturn(Optional.of(existing));

        BizException exception = assertThrows(BizException.class,
                () -> provisioner.findByHashOrCreateWithCredential(
                        IdentityTypeEnum.PHONE, IDENTIFIER_HASH, "13888888888", "$2a$new-hash", null));

        assertEquals(ErrorCode.PHONE_ALREADY_REGISTERED, exception.getErrorCode());
        verify(userAuthRepository, never()).updateByUserIdAndType(any());
        verify(userAuthRepository, never()).insert(any());
    }

    @Test
    @DisplayName("注册 — 并发撞唯一索引且回查行已有凭证 → 拒绝（不允许并发注册绕过校验）")
    void shouldReject_whenConcurrentInsertAndExistingCredentialed() {
        UserAuth raced = UserAuth.builder()
                .userId(100L).identityType(IdentityTypeEnum.PHONE).credential("$2a$raced").build();
        when(userAuthRepository.findByIdentityHash(IdentityTypeEnum.PHONE, IDENTIFIER_HASH))
                .thenReturn(Optional.empty(), Optional.empty(), Optional.of(raced));
        when(userAccountGateway.getOrCreateUserId("13888888888", IdentityTypeEnum.PHONE, null)).thenReturn(100L);
        when(userAuthRepository.insert(any(UserAuth.class)))
                .thenThrow(new DuplicateIdentityException(ErrorCode.PARAM_ERROR, "认证标识已存在"));

        assertThrows(BizException.class, () -> provisioner.findByHashOrCreateWithCredential(
                IdentityTypeEnum.PHONE, IDENTIFIER_HASH, "13888888888", "$2a$new-hash", null));
    }

    // ==================== 非敏感标识（微信） ====================

    @Test
    @DisplayName("非敏感标识 — unionId 命中 → 跨应用复用同一凭证")
    void shouldReturnByUnionId_whenUnionIdMatched() {
        UserAuth existing = UserAuth.builder().userId(100L).identityType(IdentityTypeEnum.WECHAT_MINI).build();
        when(userAuthRepository.findFirstByUnionId("unionid-1")).thenReturn(Optional.of(existing));

        UserAuth result = provisioner.findByPlainOrCreate(IdentityTypeEnum.WECHAT_MINI, "openid-1", "unionid-1");

        assertSame(existing, result);
        verify(userAuthRepository, never()).insert(any());
    }

    @Test
    @DisplayName("非敏感标识 — openid 命中 → 直接返回")
    void shouldReturnByPlain_whenOpenidMatched() {
        UserAuth existing = UserAuth.builder().userId(100L).identityType(IdentityTypeEnum.WECHAT_MINI).build();
        when(userAuthRepository.findByIdentity(IdentityTypeEnum.WECHAT_MINI, "openid-1"))
                .thenReturn(Optional.of(existing));

        UserAuth result = provisioner.findByPlainOrCreate(IdentityTypeEnum.WECHAT_MINI, "openid-1", null);

        assertSame(existing, result);
        verify(userAuthRepository, never()).findFirstByUnionId(anyString());
    }

    @Test
    @DisplayName("非敏感标识 — 都不存在 → 创建 openid 凭证并记录 unionId")
    void shouldCreate_whenOpenidNotFound() {
        when(userAuthRepository.findByIdentity(IdentityTypeEnum.WECHAT_MINI, "openid-1"))
                .thenReturn(Optional.empty());
        when(userAccountGateway.getOrCreateUserId("openid-1", IdentityTypeEnum.WECHAT_MINI, null)).thenReturn(100L);
        when(userAuthRepository.insert(any(UserAuth.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        UserAuth result = provisioner.findByPlainOrCreate(IdentityTypeEnum.WECHAT_MINI, "openid-1", "unionid-1");

        assertEquals("openid-1", result.getIdentifierPlain());
        assertEquals("unionid-1", result.getUnionId());
        verify(userAccountGateway).getOrCreateUserId(eq("openid-1"), eq(IdentityTypeEnum.WECHAT_MINI), isNull());
    }

    @Test
    @DisplayName("非敏感标识 — 网页微信（含昵称）：建档时昵称透传 user-service")
    void shouldPassNickname_whenOpenidNotFound() {
        when(userAuthRepository.findByIdentity(IdentityTypeEnum.WECHAT, "openid-web"))
                .thenReturn(Optional.empty());
        when(userAccountGateway.getOrCreateUserId("openid-web", IdentityTypeEnum.WECHAT, "微信用户"))
                .thenReturn(200L);
        when(userAuthRepository.insert(any(UserAuth.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        UserAuth result = provisioner.findByPlainOrCreate(IdentityTypeEnum.WECHAT, "openid-web", null, "微信用户");

        assertEquals(200L, result.getUserId());
        verify(userAccountGateway).getOrCreateUserId("openid-web", IdentityTypeEnum.WECHAT, "微信用户");
    }
}
