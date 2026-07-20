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
}