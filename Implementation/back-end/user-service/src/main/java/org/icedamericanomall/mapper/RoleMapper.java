package org.icedamericanomall.mapper;

import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Mapper
public interface RoleMapper {

    @Select("SELECT r.code FROM sys_role r INNER JOIN sys_user_role ur ON r.id = ur.role_id WHERE ur.user_id = #{userId}")
    Set<String> selectRoleCodesByUserId(@Param("userId") Long userId);

    @Insert("INSERT INTO sys_user_role (user_id, role_id) VALUES (#{userId}, #{roleId})")
    int insertUserRole(@Param("userId") Long userId, @Param("roleId") Long roleId);

    @Delete("DELETE FROM sys_user_role WHERE user_id = #{userId}")
    int deleteUserRoles(@Param("userId") Long userId);

    @Select("SELECT id, name, code, description, status, is_system FROM sys_role ORDER BY id")
    List<Map<String, Object>> selectAllRoles();

    @Insert("INSERT INTO sys_role (name, code, description, status, is_system, create_time, update_time) " +
            "VALUES (#{name}, #{code}, #{description}, 1, 0, NOW(), NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertRole(Map<String, Object> role);

    @Update("UPDATE sys_role SET name = #{name}, description = #{description}, update_time = NOW() WHERE id = #{id}")
    int updateRole(Map<String, Object> role);

    @Delete("DELETE FROM sys_role WHERE id = #{id} AND is_system = 0")
    int deleteRole(@Param("id") Long id);

    @Select("SELECT permission_id FROM sys_role_permission WHERE role_id = #{roleId}")
    List<Long> selectPermissionIdsByRoleId(@Param("roleId") Long roleId);

    @Delete("DELETE FROM sys_role_permission WHERE role_id = #{roleId}")
    int deleteRolePermissions(@Param("roleId") Long roleId);

    @Insert("INSERT INTO sys_role_permission (role_id, permission_id) VALUES (#{roleId}, #{permissionId})")
    int insertRolePermission(@Param("roleId") Long roleId, @Param("permissionId") Long permissionId);
}