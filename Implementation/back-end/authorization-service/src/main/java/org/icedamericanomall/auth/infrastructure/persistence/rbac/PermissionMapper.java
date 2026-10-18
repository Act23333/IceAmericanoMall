package org.icedamericanomall.auth.infrastructure.persistence.rbac;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 权限 Mapper — RBAC 五表模型（ADR-018）。
 * 支持: 用户直接权限 + 角色继承权限 双通道查询；强类型 {@link PermissionPO} 读模型。
 */
@Mapper
public interface PermissionMapper {

    // status = 1 过滤是**停用机制生效的唯一开关**（2026-10-18 实测发现两条装配查询原先都不过滤，
    // 于是 sys_permission.status = 0 纯属装饰：停用的位照样进 user:perms:、照样通过 hasPermi）。
    // 权限位拆分迁移选定的停用方式就是 status=0，故这两条查询必须一致过滤。
    @Select("SELECT p.code FROM sys_permission p INNER JOIN sys_user_permission up ON p.id = up.permission_id "
            + "WHERE up.user_id = #{userId} AND p.status = 1")
    Set<String> selectDirectPermCodesByUserId(@Param("userId") Long userId);

    @Select("SELECT p.code FROM sys_permission p " +
            "INNER JOIN sys_role_permission rp ON p.id = rp.permission_id " +
            "INNER JOIN sys_user_role ur ON rp.role_id = ur.role_id " +
            "WHERE ur.user_id = #{userId} AND p.status = 1")
    Set<String> selectRolePermCodesByUserId(@Param("userId") Long userId);

    // 管理端展示用：**不过滤** status —— RBAC 管理页要能看到并重新启用停用的位。
    // 它不参与授权判定（判定集只来自上面两条），故不受"停用"影响。
    @Select("SELECT id, name, code, type, parent_id, path, sort_order, status FROM sys_permission ORDER BY sort_order, id")
    List<PermissionPO> selectAllPermissions();
}
