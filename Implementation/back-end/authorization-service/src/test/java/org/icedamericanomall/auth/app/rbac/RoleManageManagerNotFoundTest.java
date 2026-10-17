package org.icedamericanomall.auth.app.rbac;

import org.icedamericanomall.auth.domain.model.rbac.Role;
import org.icedamericanomall.auth.domain.repository.RoleRepository;
import org.icedamericanomall.common.enums.ErrorCategory;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 角色管理的<b>错误承载拆分</b>（2026-10-18 批次 ①「not-found 归位」+ ③ 留下的双义项）。
 *
 * <p><b>本类锁死的偏离</b>：
 * <ol>
 *   <li>{@code updateRole} 受影响 0 行 = <b>角色不存在</b>，此前抛 3000（HTTP 200）→
 *       现 {@code NO_RESOURCE_FOUND}(1103 / <b>404</b>)；</li>
 *   <li>{@code deleteRole} 的 0 行<b>同时</b>覆盖「不存在」与「内置角色不可删」（删除 SQL 带
 *       {@code is_system = 0}）—— 本批<b>只拆出「不存在」那一支</b>：
 *       <ul>
 *         <li>不存在 → 1103 / 404；</li>
 *         <li>内置不可删 → <b>保留</b> 3000 / 200（业务规则拒绝：资源存在、请求合法、规则不允许）。</li>
 *       </ul>
 *       把「内置」也改成 4xx 会让运营把「系统保护」误读成「角色被删了」。</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RoleManageManager — 「不存在」404 / 「内置不可删」200，两者不得合流")
class RoleManageManagerNotFoundTest {

    private static final Long ROLE_ID = 7L;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PermissionCacheEvictor permissionCacheEvictor;

    private RoleManageManager manager;

    @BeforeEach
    void setUp() {
        manager = new RoleManageManager(roleRepository, permissionCacheEvictor);
    }

    private static Role role(boolean system) {
        return new Role(ROLE_ID, "角色", "ROLE_X", "描述", 1, system);
    }

    @Test
    @DisplayName("updateRole — 受影响 0 行 → 1103 / 404（不再是 3000 / 200）")
    void shouldThrow404_whenUpdateRoleAffectsNoRow() {
        when(roleRepository.updateRole(ROLE_ID, "新名字", "新描述")).thenReturn(0);

        BizException ex = assertThrows(BizException.class,
                () -> manager.updateRole(ROLE_ID, "新名字", "新描述"));

        assertEquals(ErrorCode.NO_RESOURCE_FOUND, ex.getErrorCode(),
                "角色不存在是资源不存在，必须 1103，不得借用 3000");
        assertEquals(404, ex.getErrorCode().getHttpStatus().value(), "not-found 语义必须真 404");
        assertEquals(ErrorCategory.CLIENT, ErrorCategory.of(ex.getErrorCode()));
    }

    @Test
    @DisplayName("deleteRole — 角色不存在 → 1103 / 404，且不执行删除、不失效缓存")
    void shouldThrow404_whenRoleMissing() {
        when(roleRepository.findById(ROLE_ID)).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> manager.deleteRole(ROLE_ID));

        assertEquals(ErrorCode.NO_RESOURCE_FOUND, ex.getErrorCode());
        assertEquals(404, ex.getErrorCode().getHttpStatus().value());
        verify(roleRepository, never()).deleteRole(anyLong());
        verify(permissionCacheEvictor, never()).evictUsers(any());
    }

    @Test
    @DisplayName("deleteRole — 内置角色 → 仍是 3000 / 200（业务规则拒绝，本批刻意不动）")
    void shouldKeepBusinessRuleRejection_whenRoleIsSystem() {
        when(roleRepository.findById(ROLE_ID)).thenReturn(role(true));

        BizException ex = assertThrows(BizException.class, () -> manager.deleteRole(ROLE_ID));

        assertEquals(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, ex.getErrorCode(),
                "内置不可删是「资源存在 + 规则不允许」，必须留在 3000");
        assertEquals(200, ex.getErrorCode().getHttpStatus().value(), "业务规则拒绝仍是 200 信封");
        assertEquals(ErrorCategory.RULE, ErrorCategory.of(ex.getErrorCode()));
        verify(roleRepository, never()).deleteRole(anyLong());
    }

    @Test
    @DisplayName("deleteRole — 正常角色 → 删除并失效受影响用户缓存（成功路径不被拆义改坏）")
    void shouldDeleteAndEvict_whenRoleDeletable() {
        when(roleRepository.findById(ROLE_ID)).thenReturn(role(false));
        when(roleRepository.findUserIdsByRoleId(ROLE_ID)).thenReturn(Set.of(11L, 12L));
        when(roleRepository.deleteRole(ROLE_ID)).thenReturn(1);

        manager.deleteRole(ROLE_ID);

        verify(roleRepository).deleteRole(ROLE_ID);
        verify(permissionCacheEvictor).evictUsers(Set.of(11L, 12L));
    }

    @Test
    @DisplayName("deleteRole — 前置校验通过但删除 0 行（并发）→ 1103 / 404，绝不冒称「内置不可删」")
    void shouldThrow404_whenConcurrentlyDeleted() {
        when(roleRepository.findById(ROLE_ID)).thenReturn(role(false));
        when(roleRepository.findUserIdsByRoleId(ROLE_ID)).thenReturn(Set.of());
        when(roleRepository.deleteRole(ROLE_ID)).thenReturn(0);

        BizException ex = assertThrows(BizException.class, () -> manager.deleteRole(ROLE_ID));

        assertEquals(ErrorCode.NO_RESOURCE_FOUND, ex.getErrorCode());
        assertEquals(404, ex.getErrorCode().getHttpStatus().value());
        verify(permissionCacheEvictor, never()).evictUsers(any());
    }
}
