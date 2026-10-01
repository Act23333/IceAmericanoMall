package org.icedamericanomall.auth.infrastructure.constant;

/**
 * Redis Key 前缀常量（认证域）。
 */
public final class RedisKeyConstants {

    /** 会话 Key 前缀：{@code login:user:refresh:{userId}:{deviceType}} */
    public static final String REFRESH_TOKEN_PREFIX = "login:user:refresh";

    /** 反查索引 Key 前缀：{@code login:user:token:key:{refreshToken}} */
    public static final String REFRESH_TOKEN_USER_PREFIX = "login:user:token:key";

    private RedisKeyConstants() {
    }
}
