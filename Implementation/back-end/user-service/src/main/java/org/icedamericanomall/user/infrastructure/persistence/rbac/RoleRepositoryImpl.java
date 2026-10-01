package org.icedamericanomall.user.infrastructure.persistence.rbac;

import lombok.RequiredArgsConstructor;
import org.icedamericanomall.user.domain.repository.RoleRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
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
    public Map<Long, Set<String>> findRoleCodesByUserIds(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Set<String>> result = new HashMap<>();
        for (Map<String, Object> row : roleMapper.selectRoleCodesByUserIds(userIds)) {
            Long userId = toLong(getIgnoreCase(row, "uid"));
            String code = getIgnoreCase(row, "code") == null ? null : String.valueOf(getIgnoreCase(row, "code"));
            if (userId == null || code == null) {
                continue;
            }
            result.computeIfAbsent(userId, k -> new HashSet<>()).add(code);
        }
        return result;
    }

    @Override
    public Set<Long> findUserIdsByRoleId(Long roleId) {
        Set<Long> userIds = roleMapper.selectUserIdsByRoleId(roleId);
        return userIds == null ? Set.of() : userIds;
    }

    /** Map 取值大小写不敏感（H2 与 MySQL 的结果集列标签大小写策略不同）。 */
    private static Object getIgnoreCase(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value != null) {
            return value;
        }
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(key)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        return value instanceof Number number ? number.longValue() : Long.valueOf(String.valueOf(value));
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
