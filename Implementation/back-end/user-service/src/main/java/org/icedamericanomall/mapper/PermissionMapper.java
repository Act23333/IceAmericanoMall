package org.icedamericanomall.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Set;

/**
 * @ClassName: PermissionMapper
 * @Description: TODO
 * @Author: noLazy
 * @Date: 2026/7/20 16:46
 * @Version: 1.0.0
 * @ProjectName: back-end
 * @Package: org.icedamericanomall.mapper
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
}