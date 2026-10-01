package org.icedamericanomall.user.app.port;

import org.icedamericanomall.common.domain.UserInfo;

import java.util.Optional;
import java.util.Set;

/**
 * 用户安全上下文缓存（应用层端口，基础设施层用 Redis 实现）。
 *
 * <p><b>跨服务契约（网关实时权限依赖，勿改）</b>：
 * <ul>
 *   <li>{@code user:info:{userId}} —— UserInfo JSON，TTL 30 分钟</li>
 *   <li>{@code user:perms:{userId}} —— 权限 SET（含 {@code ROLE_} 前缀角色），TTL 30 分钟；
 *       网关 {@code UserContextHeaderFilter} 读取该 Key 注入 {@code X-User-Roles}/{@code X-User-Permissions} header</li>
 * </ul>
 */
public interface UserSecurityCache {

    /** 读 {@code user:info:{userId}} 缓存（未命中返回 empty）。 */
    Optional<UserInfo> findUserInfo(Long userId);

    /**
     * 写两处缓存（TTL 均 30 分钟）。
     *
     * @param userInfo      用户安全上下文
     * @param permissionSet 权限 + 角色编码集合（写入 {@code user:perms:{userId}}）
     */
    void store(UserInfo userInfo, Set<String> permissionSet);
}
