package org.icedamericanomall.utils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.icedamericanomall.constants.RedisKeyConstants;
import org.icedamericanomall.domain.dto.RefreshTokenInfo;
import org.noLazy.common.enums.ErrorCode;
import org.noLazy.common.exception.UnauthorizedException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Refresh Token 双 key 设计 — 京东模式
 * <pre>
 *   refresh:token:{tokenId}       → RefreshTokenInfo (token 本体)
 *   refresh:token:user:{userId}    → String (当前活跃 tokenId, 索引)
 *
 * 三种场景互不混淆:
 *   ① 主动登录  → 无条件新建 + 踢掉旧 token（单地登录）
 *   ② 静默刷新  → 轮换新 token，继承绝对过期时间
 *   ③ 登出      → 双删（tokenKey + userKey）
 *
 * 无"复用"概念 — 主动登录永远创建新 token。
 * 无滑动续期 — Redis TTL 即绝对有效期，物理与业务一致。
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenUtils {

    private final RedisTemplate<String, Object> redisTemplate;

    private static String userKey(Long userId) {
        return RedisKeyConstants.REFRESH_TOKEN_USER_PREFIX + userId;
    }

    private static String tokenKey(String tokenId) {
        return RedisKeyConstants.REFRESH_TOKEN_PREFIX + tokenId;
    }

    /**
     * 主动登录：无条件创建新 token。
     * 若存在旧活跃 token → 删除之（新设备登录踢掉旧设备，单地登录）。
     */
    public String createRefreshToken(Long userId, String username, long ttlSeconds) {
        // 踢掉旧登录
        String prevId = (String) redisTemplate.opsForValue().get(userKey(userId));
        if (prevId != null) {
            redisTemplate.delete(tokenKey(prevId));
            log.info("踢掉旧 Refresh Token: {} for user {}", prevId, userId);
        }

        long now = System.currentTimeMillis();
        String id = UUID.randomUUID().toString().replace("-", "");
        RefreshTokenInfo info = new RefreshTokenInfo(id, userId, username, now, now + ttlSeconds * 1000);
        // 双 key 相同 TTL，物理与业务生命周期一致
        redisTemplate.opsForValue().set(tokenKey(id), info, ttlSeconds, TimeUnit.SECONDS);
        redisTemplate.opsForValue().set(userKey(userId), id, ttlSeconds, TimeUnit.SECONDS);
        log.info("创建 Refresh Token: {} for user {}, TTL={}s", id, userId, ttlSeconds);
        return id;
    }

    /**
     * 静默刷新：轮换新 token，继承原绝对过期时间（token 链总寿命受控）。
     */
    public String rotateRefreshToken(String oldId) {
        RefreshTokenInfo info = (RefreshTokenInfo) redisTemplate.opsForValue().get(tokenKey(oldId));
        if (info == null) {
            throw new UnauthorizedException(ErrorCode.TOKEN_EXPIRED, "Refresh Token 无效或已过期");
        }

        long remainingMs = info.getMaxExpireAt() - System.currentTimeMillis();
        if (remainingMs <= 0) {
            revokeRefreshToken(oldId);
            throw new UnauthorizedException(ErrorCode.TOKEN_EXPIRED, "Refresh Token 已过绝对有效期，请重新登录");
        }

        String newId = UUID.randomUUID().toString().replace("-", "");
        RefreshTokenInfo newInfo = new RefreshTokenInfo(
                newId, info.getUserId(), info.getUsername(),
                System.currentTimeMillis(), info.getMaxExpireAt()); // 继承绝对过期时间
        long ttl = remainingMs / 1000;
        redisTemplate.opsForValue().set(tokenKey(newId), newInfo, ttl, TimeUnit.SECONDS);
        redisTemplate.opsForValue().set(userKey(info.getUserId()), newId, ttl, TimeUnit.SECONDS);
        redisTemplate.delete(tokenKey(oldId));
        log.info("轮换 Refresh Token: {} → {} for user {}", oldId, newId, info.getUserId());
        return newId;
    }

    /**
     * 登出：双删（tokenKey + userKey 索引），保持状态干净。
     */
    public void revokeRefreshToken(String tokenId) {
        RefreshTokenInfo info = getInfoByToken(tokenId);
        redisTemplate.delete(tokenKey(tokenId));
        if (info != null) {
            // 仅当索引仍指向本 token 时删除（防止误删新 token 的索引）
            String currentId = (String) redisTemplate.opsForValue().get(userKey(info.getUserId()));
            if (tokenId.equals(currentId)) {
                redisTemplate.delete(userKey(info.getUserId()));
            }
        }
        log.info("登出吊销 Refresh Token: {} — {}", tokenId, info != null ? "成功" : "key 不存在");
    }

    public RefreshTokenInfo getInfoByToken(String tokenId) {
        return (RefreshTokenInfo) redisTemplate.opsForValue().get(tokenKey(tokenId));
    }
}
