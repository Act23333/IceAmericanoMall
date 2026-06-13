package org.noLazy.common.utils;

import java.util.Objects;

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
    private static final ThreadLocal<Long> threadLocal = new ThreadLocal<>();

    public static void setUser(Long userId) {
        threadLocal.set(userId);
    }

    public static Long getUser() {
        return threadLocal.get();
    }

    public static void removeUser() {
        threadLocal.remove();
    }

}
