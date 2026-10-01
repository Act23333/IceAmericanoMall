package org.icedamericanomall.user.infrastructure.persistence.rbac;

import lombok.RequiredArgsConstructor;
import org.icedamericanomall.user.domain.repository.RoleRepository;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * RBAC 仓储实现（基础设施层）—— sys_role / sys_permission / sys_user_role /
 * sys_role_permission / sys_user_permission 五表模型。
 *
 * <p>读方法返回契约形状的 {@code Map}（key 为列名，直接序列化为响应 JSON）；
 * 唯一性约束与内置角色保护由 SQL 层承担（如 {@code deleteRole} 带 {@code is_system=0}）。
 */
@Repository
@RequiredArgsConstructor
public class RoleRepositoryImpl implements RoleRepository {

    private final RoleMapper roleMapper;
    private final PermissionMapper permissionMapper;

    // ========== 角色 ==========

    @Override
    public List<Map<String, Object>> findAllRoles() {
        return roleMapper.selectAllRoles();
    }

    @Override
    public Map<String, Object> insertRole(String name, String code, String description) {
        Map<String, Object> role = new HashMap<>();
        role.put("name", name);
        role.put("code", code);
        role.put("description", description);
        roleMapper.insertRole(role);
        return role;
    }

    @Override
    public int updateRole(Long id, String name, String description) {
        Map<String, Object> role = new HashMap<>();
        role.put("id", id);
        role.put("name", name);
        role.put("description", description);
        return roleMapper.updateRole(role);
    }

    @Override
    public int deleteRole(Long id) {
        return roleMapper.deleteRole(id);
    }

    // ========== 角色-权限 ==========

    @Override
    public List<Long> findPermissionIdsByRoleId(Long roleId) {
        return roleMapper.selectPermissionIdsByRoleId(roleId);
    }

    @Override
    public int deleteRolePermissions(Long roleId) {
        return roleMapper.deleteRolePermissions(roleId);
    }

    @Override
    public int insertRolePermission(Long roleId, Long permissionId) {
        return roleMapper.insertRolePermission(roleId, permissionId);
    }

    @Override
    public List<Map<String, Object>> findAllPermissions() {
        return permissionMapper.selectAllPermissions();
    }

    // ========== 用户-角色 / 权限编码 ==========

    @Override
    public int deleteUserRoles(Long userId) {
        return roleMapper.deleteUserRoles(userId);
    }

    @Override
    public int insertUserRole(Long userId, Long roleId) {
        return roleMapper.insertUserRole(userId, roleId);
    }

    @Override
    public Set<String> findRoleCodesByUserId(Long userId) {
        return roleMapper.selectRoleCodesByUserId(userId);
    }

    @Override
    public Set<String> findDirectPermissionCodesByUserId(Long userId) {
        return permissionMapper.selectDirectPermCodesByUserId(userId);
    }

    @Override
    public Set<String> findRolePermissionCodesByUserId(Long userId) {
        return permissionMapper.selectRolePermCodesByUserId(userId);
    }
}
