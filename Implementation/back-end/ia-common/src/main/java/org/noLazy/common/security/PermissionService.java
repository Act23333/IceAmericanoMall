package org.noLazy.common.security;

import lombok.extern.slf4j.Slf4j;
import org.noLazy.common.domain.UserInfo;
import org.noLazy.common.utils.UserContext;
import org.springframework.stereotype.Service;

import java.util.Arrays;

/**
 * 大厂标准: 集中式权限服务 — 供 @PreAuthorize("@ss.hasPermi('order:read')") 调用。
 * <p>
 * Bean 命名为 "ss" 是 Alibaba RuoYi 等大厂开源项目的约定俗成，
 * 便于在 SpEL 表达式中简短引用。
 * <p>
 * 权限数据来源: UserContext (由网关通过 Headers 传递，UserContextFilter 重建)。
 * 支持通配符 {@code *:*:*} 表示超级管理员权限。
 */
@Slf4j
@Service("ss")
public class PermissionService {

    /** 超级管理员通配符 */
    private static final String SUPER_ADMIN_PERM = "*:*:*";

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
        // 超级管理员通配符
        if (user.permissions().contains(SUPER_ADMIN_PERM)) {
            return true;
        }
        // ROLE_ADMIN 角色默认拥有所有权限
        if (user.roles() != null && user.roles().contains("ROLE_ADMIN")) {
            return true;
        }
        return user.permissions().contains(permission);
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
