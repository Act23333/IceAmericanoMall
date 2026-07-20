package org.noLazy.common.utils;



import org.noLazy.common.domain.UserInfo;


/**
 * @ClassName: UserContext
 * @Description: TODO
 * @Author: noLazy
 * @Date: 2026/3/21 20:33
 * @Version: 1.0.0
 * @ProjectName: IcedAmericanoMall
 * @Package: org.noLazy.common.utils
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

    /** 检查当前用户是否拥有指定权限 */
    public static boolean hasPermission(String permission) {
        UserInfo user = HOLDER.get();
        return user != null && user.permissions() != null && user.permissions().contains(permission);
    }

    /** 检查当前用户是否拥有任一指定权限 */
    public static boolean hasAnyPermission(String... permissions) {
        UserInfo user = HOLDER.get();
        if (user == null || user.permissions() == null) return false;
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