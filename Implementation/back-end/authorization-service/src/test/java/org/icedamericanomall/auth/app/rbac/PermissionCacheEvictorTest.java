package org.icedamericanomall.auth.app.rbac;

import org.icedamericanomall.auth.app.port.UserSecurityCache;
import org.icedamericanomall.auth.domain.repository.RoleRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 权限缓存主动失效器单元测试 —— 授权变更后 user:perms / user:info 的收敛点。
 * （随 RBAC 归属授权域迁入，ADR-018）
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PermissionCacheEvictor 单元测试")
class PermissionCacheEvictorTest {

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private UserSecurityCache userSecurityCache;

    @InjectMocks
    private PermissionCacheEvictor evictor;

    @Test
    @DisplayName("evictUser — 直接失效该用户缓存；null 安全")
    void shouldEvictUser() {
        evictor.evictUser(100L);

        verify(userSecurityCache).evict(100L);

        evictor.evictUser(null);
        verify(userSecurityCache, never()).evict(null);
    }

    @Test
    @DisplayName("evictByRole — 按 role_id 反查受影响用户并逐个失效")
    void shouldEvictByRole() {
        when(roleRepository.findUserIdsByRoleId(3L)).thenReturn(Set.of(100L, 200L));

        evictor.evictByRole(3L);

        verify(userSecurityCache).evict(100L);
        verify(userSecurityCache).evict(200L);
    }

    @Test
    @DisplayName("evictByRole — 无用户持有该角色：不产生失效调用")
    void shouldSkip_whenNoAffectedUsers() {
        when(roleRepository.findUserIdsByRoleId(9L)).thenReturn(Set.of());

        evictor.evictByRole(9L);

        verify(userSecurityCache, never()).evict(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    @DisplayName("evictUsers — 批量失效；空集合 null 安全")
    void shouldEvictUsers_inBatch() {
        evictor.evictUsers(List.of(1L, 2L, 3L));
        verify(userSecurityCache).evict(1L);
        verify(userSecurityCache).evict(2L);
        verify(userSecurityCache).evict(3L);

        evictor.evictUsers(Set.of());
        evictor.evictUsers(null);
    }

    @Test
    @DisplayName("失效失败只告警不抛出（缓存可自愈，不阻断授权写主链路）")
    void shouldNotThrow_whenCacheFails() {
        doThrow(new RuntimeException("redis down")).when(userSecurityCache).evict(100L);

        evictor.evictUser(100L);

        verify(userSecurityCache).evict(100L);
    }
}
