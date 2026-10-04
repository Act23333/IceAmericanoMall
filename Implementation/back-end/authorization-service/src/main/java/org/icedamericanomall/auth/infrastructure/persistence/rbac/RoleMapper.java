package org.icedamericanomall.auth.infrastructure.persistence.rbac;

import org.apache.ibatis.annotations.*;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * RBAC 角色 Mapper —— sys_role / sys_user_role / sys_role_permission（ADR-018）。
 * 角色读写使用强类型 {@link RolePO}；关联表（user_role / role_permission）为纯关联，无实体。
 */
@Mapper
public interface RoleMapper {

    @Select("SELECT id, name, code, description, status, is_system FROM sys_role ORDER BY id")
    List<RolePO> selectAllRoles();

    @Insert("INSERT INTO sys_role (name, code, description, status, is_system, create_time, update_time) " +
            "VALUES (#{name}, #{code}, #{description}, 1, 0, NOW(), NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertRole(RolePO role);

    @Update("UPDATE sys_role SET name = #{name}, description = #{description}, update_time = NOW() WHERE id = #{id}")
    int updateRole(@Param("id") Long id, @Param("name") String name, @Param("description") String description);

    @Delete("DELETE FROM sys_role WHERE id = #{id} AND is_system = 0")
    int deleteRole(@Param("id") Long id);

    @Select("SELECT r.code FROM sys_role r INNER JOIN sys_user_role ur ON r.id = ur.role_id WHERE ur.user_id = #{userId}")
    Set<String> selectRoleCodesByUserId(@Param("userId") Long userId);

    /**
     * 批量查询用户角色编码（管理后台列表派生 roleType，避免 N+1）。
     * 返回行：{@code uid}（user_id） + {@code code}（role code）。
     */
    @Select("<script>"
            + "SELECT ur.user_id AS uid, r.code AS code FROM sys_user_role ur "
            + "INNER JOIN sys_role r ON r.id = ur.role_id "
            + "WHERE ur.user_id IN "
            + "<foreach item='uid' collection='userIds' open='(' separator=',' close=')'>#{uid}</foreach>"
            + "</script>")
    List<Map<String, Object>> selectRoleCodesByUserIds(@Param("userIds") Collection<Long> userIds);

    /** 按角色反查持有该角色的用户（权限缓存主动失效：role_permission 变更后逐用户删除）。 */
    @Select("SELECT user_id FROM sys_user_role WHERE role_id = #{roleId}")
    Set<Long> selectUserIdsByRoleId(@Param("roleId") Long roleId);

    @Insert("INSERT INTO sys_user_role (user_id, role_id) VALUES (#{userId}, #{roleId})")
    int insertUserRole(@Param("userId") Long userId, @Param("roleId") Long roleId);

    @Delete("DELETE FROM sys_user_role WHERE user_id = #{userId}")
    int deleteUserRoles(@Param("userId") Long userId);

    @Select("SELECT permission_id FROM sys_role_permission WHERE role_id = #{roleId}")
    List<Long> selectPermissionIdsByRoleId(@Param("roleId") Long roleId);

    @Delete("DELETE FROM sys_role_permission WHERE role_id = #{roleId}")
    int deleteRolePermissions(@Param("roleId") Long roleId);

    @Insert("INSERT INTO sys_role_permission (role_id, permission_id) VALUES (#{roleId}, #{permissionId})")
    int insertRolePermission(@Param("roleId") Long roleId, @Param("permissionId") Long permissionId);
}
