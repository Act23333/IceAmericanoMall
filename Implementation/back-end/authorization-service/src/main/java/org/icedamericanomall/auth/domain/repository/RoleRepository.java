package org.icedamericanomall.auth.domain.repository;

import org.icedamericanomall.auth.domain.model.rbac.Permission;
import org.icedamericanomall.auth.domain.model.rbac.Role;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * RBAC 仓储（领域层定义，基础设施层实现）—— sys_role / sys_permission 五表模型。
 * （归属自 user-service 迁入，ADR-018；读模型已强类型化）
 *
 * <p>唯一性/内置角色保护等不变量由 SQL 约束（如 {@code is_system=0} 才允许删除）承担。
 */
public interface RoleRepository {

    // ========== 角色 ==========

    /** 全部角色（后台下拉/列表）。 */
    List<Role> findAllRoles();

    /** 新建角色（返回含生成主键的角色）。 */
    Role insertRole(String name, String code, String description);

    /** 更新角色名称/描述。 */
    int updateRole(Long id, String name, String description);

    /** 删除角色（SQL 层禁止删除内置角色 is_system=1）。 */
    int deleteRole(Long id);

    // ========== 角色-权限 ==========

    /** 角色已分配的权限 ID 列表。 */
    List<Long> findPermissionIdsByRoleId(Long roleId);

    /** 清空角色权限。 */
    int deleteRolePermissions(Long roleId);

    /** 授予角色权限。 */
    int insertRolePermission(Long roleId, Long permissionId);

    /** 全部权限（菜单/按钮/API 三级列表）。 */
    List<Permission> findAllPermissions();

    // ========== 用户-角色 / 权限编码 ==========

    /** 清空用户角色。 */
    int deleteUserRoles(Long userId);

    /** 授予用户角色。 */
    int insertUserRole(Long userId, Long roleId);

    /** 用户角色编码集合（ROLE_ 前缀，登录响应 & user:perms 网关缓存）。 */
    Set<String> findRoleCodesByUserId(Long userId);

    /**
     * 批量查询用户角色编码（管理后台列表派生 roleType 用，避免逐行 N+1）。
     *
     * @param userIds 用户技术主键集合（空集合返回空 Map）
     * @return userId → 角色编码集合（无角色的用户不在 Map 中）
     */
    Map<Long, Set<String>> findRoleCodesByUserIds(Collection<Long> userIds);

    /**
     * 按角色反查持有该角色的用户（权限缓存主动失效：sys_role_permission 变更后
     * 需要逐用户删除 {@code user:perms:{userId}}）。
     *
     * @param roleId 角色 ID
     * @return 持有该角色的用户技术主键集合
     */
    Set<Long> findUserIdsByRoleId(Long roleId);

    /** 用户个人直接权限编码集合。 */
    Set<String> findDirectPermissionCodesByUserId(Long userId);

    /** 用户经角色继承的权限编码集合。 */
    Set<String> findRolePermissionCodesByUserId(Long userId);
}
