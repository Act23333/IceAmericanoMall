package org.icedamericanomall.auth.domain;

import org.icedamericanomall.auth.domain.exception.DuplicateIdentityException;
import org.icedamericanomall.auth.domain.gateway.UserAccountGateway;
import org.icedamericanomall.auth.domain.model.UserAuth;
import org.icedamericanomall.auth.domain.port.DistributedLock;
import org.icedamericanomall.auth.domain.repository.UserAuthRepository;
import org.icedamericanomall.auth.domain.service.UserAuthProvisioner;
import org.icedamericanomall.common.enums.IdentityTypeEnum;
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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * UserAuthProvisioner 领域服务单元测试 —— 查找优先、加锁创建、并发冲突回查。
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
        when(userAccountGateway.getOrCreateUserId("13888888888", IdentityTypeEnum.PHONE)).thenReturn(100L);
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
        when(userAccountGateway.getOrCreateUserId("13888888888", IdentityTypeEnum.PHONE)).thenReturn(100L);
        when(userAuthRepository.insert(any(UserAuth.class)))
                .thenThrow(new DuplicateIdentityException(
                        org.icedamericanomall.common.enums.ErrorCode.PARAM_ERROR, "认证标识已存在"));

        UserAuth result = provisioner.findByHashOrCreate(IdentityTypeEnum.PHONE, IDENTIFIER_HASH, "13888888888");

        assertSame(existing, result);
    }

    @Test
    @DisplayName("敏感标识 — 撞索引且回查为空 → 抛出 DBException（不吞异常）")
    void shouldThrowDbException_whenDuplicateAndReloadEmpty() {
        when(userAuthRepository.findByIdentityHash(IdentityTypeEnum.PHONE, IDENTIFIER_HASH))
                .thenReturn(Optional.empty());
        when(userAccountGateway.getOrCreateUserId("13888888888", IdentityTypeEnum.PHONE)).thenReturn(100L);
        when(userAuthRepository.insert(any(UserAuth.class)))
                .thenThrow(new DuplicateIdentityException(
                        org.icedamericanomall.common.enums.ErrorCode.PARAM_ERROR, "认证标识已存在"));

        assertThrows(DBException.class,
                () -> provisioner.findByHashOrCreate(IdentityTypeEnum.PHONE, IDENTIFIER_HASH, "13888888888"));
    }

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
        when(userAccountGateway.getOrCreateUserId("openid-1", IdentityTypeEnum.WECHAT_MINI)).thenReturn(100L);
        when(userAuthRepository.insert(any(UserAuth.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        UserAuth result = provisioner.findByPlainOrCreate(IdentityTypeEnum.WECHAT_MINI, "openid-1", "unionid-1");

        assertEquals("openid-1", result.getIdentifierPlain());
        assertEquals("unionid-1", result.getUnionId());
        verify(userAccountGateway).getOrCreateUserId(eq("openid-1"), eq(IdentityTypeEnum.WECHAT_MINI));
    }
}
