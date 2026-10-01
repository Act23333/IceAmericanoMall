package org.icedamericanomall.user.infrastructure.adapter;

import lombok.RequiredArgsConstructor;
import org.icedamericanomall.common.domain.UserInfo;
import org.icedamericanomall.common.utils.JsonUtils;
import org.icedamericanomall.user.app.port.UserSecurityCache;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 用户安全上下文缓存适配器（Redis 实现）。
 *
 * <p><b>网关依赖的跨服务契约（语义不变）</b>：
 * <ul>
 *   <li>{@code user:info:{userId}} —— UserInfo JSON，TTL 30 分钟</li>
 *   <li>{@code user:perms:{userId}} —— 权限 SET（含 ROLE_ 角色），TTL 30 分钟，先删后写</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class RedisUserSecurityCacheAdapter implements UserSecurityCache {

    /** 缓存 Key 前缀：用户安全上下文 */
    private static final String USER_INFO_KEY_PREFIX = "user:info:";

    /** 缓存 Key 前缀：权限集合（网关 SISMEMBER O(1) 查询，30 分钟 TTL） */
    private static final String PERMS_KEY_PREFIX = "user:perms:";

    /** 缓存有效期（分钟） */
    private static final long TTL_MINUTES = 30;

    private final StringRedisTemplate redisTemplate;
    private final JsonUtils jsonUtils;

    @Override
    public Optional<UserInfo> findUserInfo(Long userId) {
        String cached = redisTemplate.opsForValue().get(USER_INFO_KEY_PREFIX + userId);
        return cached == null ? Optional.empty() : Optional.of(jsonUtils.fromJson(cached, UserInfo.class));
    }

    @Override
    public void store(UserInfo userInfo, Set<String> permissionSet) {
        // (a) UserInfo JSON 缓存 (30 分钟)
        redisTemplate.opsForValue().set(USER_INFO_KEY_PREFIX + userInfo.userId(),
                jsonUtils.toJson(userInfo), TTL_MINUTES, TimeUnit.MINUTES);

        // (b) 权限 SET 缓存 (网关读取；先删后写保证不残留旧权限)
        String permsKey = PERMS_KEY_PREFIX + userInfo.userId();
        redisTemplate.delete(permsKey);
        if (permissionSet != null && !permissionSet.isEmpty()) {
            redisTemplate.opsForSet().add(permsKey, permissionSet.toArray(new String[0]));
        }
        redisTemplate.expire(permsKey, TTL_MINUTES, TimeUnit.MINUTES);
    }
}
