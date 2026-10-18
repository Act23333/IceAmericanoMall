package org.icedamericanomall.common.security;


/**
 * @ClassName: UserContext
 * @Description: TODO
 * @Author: noLazy
 * @Date: 2026/3/21 20:33
 * @Version: 1.0.0
 * @ProjectName: IcedAmericanoMall
 * @Package: org.icedamericanomall.common.security
 */

public class UserContext {
    private static final ThreadLocal<UserInfo> HOLDER = new ThreadLocal<>();

    public static void setUser(UserInfo user) {
        HOLDER.set(user);
    }

    public static UserInfo getUser() {
        return HOLDER.get();
    }

    public static void removeUser() {
        HOLDER.remove();  // 必须 remove，防止内存泄漏
    }

    /** @return 当前用户ID，未登录返回 null */
    public static Long getUserId() {
        UserInfo user = HOLDER.get();
        return user != null ? user.userId() : null;
    }

    // ──────────────── 便捷方法 ────────────────
    //
    // ⚠ 与 PermissionService 的语义对齐（2026-10-18，RBAC0 前置）：通配符 `*:*:*` 必须同样生效。
    //   在此之前这里**只做精确匹配**，于是同一个管理员在两条判定路径上结论不同：
    //     @ss.hasPermi('user:admin')        → 有 `*:*:*` 即通过
    //     UserContext.hasPermission('user:admin') → 只看字面量，`*:*:*` 不通过
    //   今天两份种子数据让它们恰好一致（ROLE_ADMIN 同时持有 `*:*:*` 与 `user:admin`），
    //   但一旦按公司口径「给管理员收窄成只留 `*:*:*`」，使用本方法的服务
    //   （pay / merchant / ai / logistics —— 它们没引 spring-security，用不了 @PreAuthorize）
    //   会**开始拒绝管理员**。这是个只会在大改时炸的隐性分叉，故此处统一。

    /** 超级管理员通配符（与 {@code PermissionService.SUPER_ADMIN_PERM} 同一取值，勿各写一份）。 */
    private static final String SUPER_ADMIN_PERM = "*:*:*";

    /** 检查当前用户是否拥有指定权限（`*:*:*` 通配；无上下文即拒绝）。 */
    public static boolean hasPermission(String permission) {
        UserInfo user = HOLDER.get();
        if (user == null || user.permissions() == null) {
            return false;
        }
        return user.permissions().contains(SUPER_ADMIN_PERM) || user.permissions().contains(permission);
    }

    /** 检查当前用户是否拥有任一指定权限（`*:*:*` 通配）。 */
    public static boolean hasAnyPermission(String... permissions) {
        UserInfo user = HOLDER.get();
        if (user == null || user.permissions() == null) return false;
        if (user.permissions().contains(SUPER_ADMIN_PERM)) return true;
        for (String perm : permissions) {
            if (user.permissions().contains(perm)) return true;
        }
        return false;
    }

    /** 检查当前用户是否拥有指定角色 */
    public static boolean hasRole(String role) {
        UserInfo user = HOLDER.get();
        return user != null && user.roles() != null && user.roles().contains(role);
    }
}