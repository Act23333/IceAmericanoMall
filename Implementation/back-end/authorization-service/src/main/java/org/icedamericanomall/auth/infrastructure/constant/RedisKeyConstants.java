package org.icedamericanomall.auth.infrastructure.constant;

/**
 * Redis Key 前缀常量（认证域）。
 *
 * <p>本类中的前缀均为<b>跨服务契约键</b>的组成部分（网关读取），改名须两侧同步发布，见 ADR-031（多粒度吊销）。
 */
public final class RedisKeyConstants {

    /** 会话 Key 前缀：{@code login:user:refresh:{userId}:{deviceType}} */
    public static final String REFRESH_TOKEN_PREFIX = "login:user:refresh";

    /** 反查索引 Key 前缀：{@code login:user:token:key:{refreshToken}} */
    public static final String REFRESH_TOKEN_USER_PREFIX = "login:user:token:key";

    /**
     * JWT 吊销黑名单 Key 前缀：{@code auth:blacklist:{jti}}。
     *
     * <p>由 gateway-service 的 {@code JwtBlacklistFilter} 读取（与 {@code user:info:} /
     * {@code user:perms:} 同级，见 ADR-027）；写侧为
     * {@link org.icedamericanomall.auth.infrastructure.adapter.TokenBlacklistServiceImpl}。
     */
    public static final String JWT_BLACKLIST_PREFIX = "auth:blacklist:";

    /**
     * 用户级令牌版本 Key 前缀：{@code auth:tokenver:{userId}}。
     *
     * <p>值是单调递增的版本号（{@code INCR}），键缺失视为版本 {@code 0}。
     * 由 gateway-service 的 {@code JwtBlacklistFilter} 读取（与 {@code user:info:} /
     * {@code user:perms:} / {@code auth:blacklist:} 同级，见 ADR-031）；写侧为
     * {@link org.icedamericanomall.auth.infrastructure.adapter.TokenVersionServiceImpl}。
     *
     * <p><b>不设 TTL</b>：一旦过期，版本会回落为 {@code 0}，已吊销令牌将被"复活"。
     */
    public static final String JWT_TOKEN_VERSION_PREFIX = "auth:tokenver:";

    private RedisKeyConstants() {
    }
}
