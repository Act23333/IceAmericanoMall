package org.icedamericanomall.user.app.rbac;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.user.domain.repository.RoleRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * RBAC 角色管理（应用层）—— 角色 CRUD + 角色权限分配 + 用户角色设置。
 *
 * <p>调用链: Adapter → 本类（Application） → {@code RoleRepository}（Infrastructure），
 * 不直接依赖 Mapper。内置角色保护由 SQL 层（{@code is_system=0}）承担。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RoleManageManager {

    /** roleType → sys_role.id 映射: 0→1(USER), 1→3(SELLER), 2→4(ADMIN) */
    private static final Long[] ROLE_MAP = {1L, 3L, 4L};

    /** 角色 ID —— ROLE_USER（商家保留普通用户角色） */
    private static final Long ROLE_USER_ID = 1L;

    private final RoleRepository roleRepository;

    /** 全部角色。 */
    public List<Map<String, Object>> listAllRoles() {
        return roleRepository.findAllRoles();
    }

    /** 新建角色。 */
    public Map<String, Object> createRole(String name, String code, String description) {
        if (name == null || name.isBlank() || code == null || code.isBlank()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "角色名称和编码不能为空");
        }
        Map<String, Object> role = roleRepository.insertRole(name, code, description);
        log.info("角色创建成功: name={}, code={}", name, code);
        return role;
    }

    /** 更新角色名称/描述。 */
    public void updateRole(Long id, String name, String description) {
        int rows = roleRepository.updateRole(id, name, description);
        if (rows <= 0) {
            throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "角色不存在");
        }
        log.info("角色更新成功: id={}", id);
    }

    /** 删除角色（内置角色在 SQL 层拒绝）。 */
    public void deleteRole(Long id) {
        int rows = roleRepository.deleteRole(id);
        if (rows <= 0) {
            throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "角色不存在或为系统内置角色不可删除");
        }
        log.info("角色删除成功: id={}", id);
    }

    /** 角色已分配的权限 ID 列表。 */
    public List<Long> getRolePermissions(Long roleId) {
        return roleRepository.findPermissionIdsByRoleId(roleId);
    }

    /** 覆盖式分配角色权限（先清后建）。 */
    public void assignPermissions(Long roleId, List<Long> permissionIds) {
        if (permissionIds == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "permissionIds 不能为空");
        }
        roleRepository.deleteRolePermissions(roleId);
        for (Long permId : permissionIds) {
            roleRepository.insertRolePermission(roleId, permId);
        }
        log.info("角色权限分配成功: roleId={}, permCount={}", roleId, permissionIds.size());
    }

    /**
     * 设置用户角色（覆盖式；商家同时保留 ROLE_USER）。
     *
     * <p>V5.0: roleType → sys_role.id 映射: 0→1(USER), 1→3(SELLER), 2→4(ADMIN)。
     */
    public void setUserRole(Long userId, Integer roleType) {
        if (roleType == null || roleType < 0 || roleType >= ROLE_MAP.length) {
            throw new BizException(ErrorCode.PARAM_ERROR, "无效的角色类型: " + roleType);
        }
        roleRepository.deleteUserRoles(userId);
        roleRepository.insertUserRole(userId, ROLE_MAP[roleType]);
        if (roleType == 1) {
            roleRepository.insertUserRole(userId, ROLE_USER_ID);
        }
        log.info("用户角色已更新: userId={}, roleType={}", userId, roleType);
    }
}
