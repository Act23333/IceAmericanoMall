package org.icedamericanomall.common.security;

import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.common.security.UserInfo;
import org.icedamericanomall.common.security.UserContext;
import org.springframework.stereotype.Service;

import java.util.Arrays;

/**
 * 集中式权限判定服务 —— 供 {@code @PreAuthorize("@ss.hasPermi('order:read')")} 调用（RBAC0 的唯一判定入口）。
 *
 * <p>Bean 命名为 {@code "ss"} 沿 RuoYi 等项目的约定，便于在 SpEL 里简短引用。
 *
 * <p>权限数据来源：{@link UserContext}（网关经 Headers 传递，{@code UserContextFilter} 重建）。
 *
 * <p><b>三条判定规则（就这三条，别再加旁路）</b>：
 * <ol>
 *   <li><b>fail-closed</b>：无用户上下文、或无权限集合 ⇒ <b>拒绝</b>（不是放行）；</li>
 *   <li><b>通配符</b>：集合含 {@code *:*:*} ⇒ 通过（超级管理员的<b>唯一</b>载体）；</li>
 *   <li><b>精确匹配</b>：集合含该权限串 ⇒ 通过。</li>
 * </ol>
 *
 * <p><b>⚠ 已移除「{@code roles} 含 ROLE_ADMIN 即放行」的短路（2026-10-18）</b>：那条短路让
 * <b>权限位对管理员完全失效</b> —— 即使把管理员的权限全删光，他也照样通过一切 {@code @PreAuthorize}，
 * 于是「管理员也不能做 X」这种要求<b>无法表达</b>。移除的前提已核实：
 * {@code Initialize.sql} 里 {@code ROLE_ADMIN} 持有 {@code permission_id=1}（{@code *:*:*}），
 * 而权限缓存 {@code SecurityContextManager} 会把「角色权限码」并入 {@code user:perms:{userId}}，
 * 所以管理员仍经<b>通配符</b>全通，行为不变；但从此可以给管理员做<b>收窄</b>（去掉 {@code *:*:*}、只留具体位）。
 *
 * <p><b>迁移/缓存口径</b>：本改动不改任何角色-权限数据，故缓存无需清除；
 * 若要给管理员收窄，必须同时 {@code DEL user:perms:{userId}}（TTL 30 分钟，最迟半小时自然收敛）。
 */
@Slf4j
@Service("ss")
public class PermissionService {

    /**
     * 检查当前用户是否拥有指定权限。
     * <p>
     * 用法: {@code @PreAuthorize("@ss.hasPermi('user:admin')")}
     *
     * @param permission 权限编码 (如 "order:read", "user:admin")
     * @return true 如果有权限
     */
    public boolean hasPermi(String permission) {
        UserInfo user = UserContext.getUser();
        if (user == null || user.permissions() == null) {
            return false;
        }
        // 规则②③统一在 PermissionBits（唯一实现）：通配符 + 精确匹配 + Q6 的 rbac:admin 排除。
        // **刻意没有「ROLE_ADMIN ⇒ 放行」的短路**（见类注释）。
        return PermissionBits.grants(user.permissions(), permission);
    }

    /**
     * 检查当前用户是否满足任一权限。
     * <p>
     * 用法: {@code @PreAuthorize("@ss.hasAnyPermi('user:admin', 'seller:admin')")}
     */
    public boolean hasAnyPermi(String... permissions) {
        if (permissions == null || permissions.length == 0) return false;
        return Arrays.stream(permissions).anyMatch(this::hasPermi);
    }

    /**
     * 检查当前用户是否满足所有权限。
     * <p>
     * 用法: {@code @PreAuthorize("@ss.hasAllPermi('order:read', 'order:write')")}
     */
    public boolean hasAllPermi(String... permissions) {
        if (permissions == null || permissions.length == 0) return false;
        return Arrays.stream(permissions).allMatch(this::hasPermi);
    }

    /**
     * 检查当前用户是否拥有指定角色。
     * <p>
     * 用法: {@code @PreAuthorize("@ss.hasRole('ROLE_ADMIN')")}
     */
    public boolean hasRole(String role) {
        UserInfo user = UserContext.getUser();
        return user != null && user.roles() != null && user.roles().contains(role);
    }

    /**
     * 检查当前用户是否满足任一角色。
     */
    public boolean hasAnyRole(String... roles) {
        if (roles == null || roles.length == 0) return false;
        return Arrays.stream(roles).anyMatch(this::hasRole);
    }
}
