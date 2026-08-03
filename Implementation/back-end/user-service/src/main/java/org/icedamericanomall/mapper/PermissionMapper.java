package org.icedamericanomall.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 权限 Mapper — RBAC 五表模型
 * 支持: 用户直接权限 + 角色继承权限 双通道查询
 */
@Mapper
public interface PermissionMapper {

    @Select("SELECT p.code FROM sys_permission p INNER JOIN sys_user_permission up ON p.id = up.permission_id WHERE up.user_id = #{userId}")
    Set<String> selectDirectPermCodesByUserId(@Param("userId") Long userId);

    @Select("SELECT p.code FROM sys_permission p " +
            "INNER JOIN sys_role_permission rp ON p.id = rp.permission_id " +
            "INNER JOIN sys_user_role ur ON rp.role_id = ur.role_id " +
            "WHERE ur.user_id = #{userId}")
    Set<String> selectRolePermCodesByUserId(@Param("userId") Long userId);

    @Select("SELECT id, name, code, type, parent_id, path, sort_order, status FROM sys_permission ORDER BY sort_order, id")
    List<Map<String, Object>> selectAllPermissions();
}