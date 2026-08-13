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
 * Refresh Token 双 key 设计：
 * <pre>
 *   refresh:token:{tokenId}       → RefreshTokenInfo
 *   refresh:token:user:{userId}    → String (当前活跃 tokenId)
 * </pre>
 * 登录时若已有活跃 token → 刷新 TTL 复用（不创建新的）；
 * 轮换时创建新 token → 同步更新用户索引 key。
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

    /** 登录：已有活跃 token → 刷新 TTL 复用；超过绝对过期时间 → 强制新建。 */
    public String createRefreshToken(Long userId, String username, long ttlSeconds) {
        String prevId = (String) redisTemplate.opsForValue().get(userKey(userId));
        if (prevId != null) {
            RefreshTokenInfo prev = (RefreshTokenInfo) redisTemplate.opsForValue().get(tokenKey(prevId));
            // ⚠️ 安全修复: 复用前必须校验 maxExpireAt，防止定期登录让 refresh token 永不过期
            if (prev != null && prev.getMaxExpireAt() > System.currentTimeMillis()) {
                // Redis TTL 封顶在绝对过期剩余时间内，保证 Redis 过期与逻辑过期一致
                long remainingSec = (prev.getMaxExpireAt() - System.currentTimeMillis()) / 1000;
                long effectiveTtl = Math.min(ttlSeconds, remainingSec);
                redisTemplate.expire(tokenKey(prevId), effectiveTtl, TimeUnit.SECONDS);
                redisTemplate.expire(userKey(userId), effectiveTtl, TimeUnit.SECONDS);
                log.info("复用已有 Refresh Token: {} for user {}, 剩余绝对寿命 {}s", prevId, userId, effectiveTtl);
                return prevId;
            }
            // 旧 token 已达绝对过期时间 → 清理后走新建流程
            if (prev != null) {
                revokeRefreshToken(prevId);
                log.info("Refresh Token 达绝对过期，强制新建: {} for user {}", prevId, userId);
            }
        }
        long now = System.currentTimeMillis();
        // 绝对过期时间 = 滑动TTL × 4（京东标准: 滑动7天+绝对28天，定期登录最多续到28天）
        long maxExpireAt = now + ttlSeconds * 4 * 1000;
        String id = UUID.randomUUID().toString().replace("-", "");
        RefreshTokenInfo info = new RefreshTokenInfo(id, userId, username, now, maxExpireAt);
        redisTemplate.opsForValue().set(tokenKey(id), info, ttlSeconds, TimeUnit.SECONDS);
        redisTemplate.opsForValue().set(userKey(userId), id, ttlSeconds, TimeUnit.SECONDS);
        log.info("创建 Refresh Token: {} for user {}", id, userId);
        return id;
    }

    /** 轮换：旧 token → 新 token，同步更新用户索引。TTL 维持原绝对过期时间。 */
    public String rotateRefreshToken(String oldId) {
        RefreshTokenInfo info = (RefreshTokenInfo) redisTemplate.opsForValue().get(tokenKey(oldId));
        if (info == null) {
            throw new UnauthorizedException(ErrorCode.TOKEN_EXPIRED, "Refresh Token 无效或已过期");
        }
        if (info.getMaxExpireAt() < System.currentTimeMillis()) {
            revokeRefreshToken(oldId);
            throw new UnauthorizedException(ErrorCode.TOKEN_EXPIRED, "Refresh Token 已过期");
        }
        String newId = UUID.randomUUID().toString().replace("-", "");
        RefreshTokenInfo newInfo = new RefreshTokenInfo(
                newId, info.getUserId(), info.getUsername(),
                System.currentTimeMillis(), info.getMaxExpireAt());
        long ttl = (newInfo.getMaxExpireAt() - System.currentTimeMillis()) / 1000;
        if (ttl <= 0) {
            throw new UnauthorizedException(ErrorCode.TOKEN_EXPIRED, "Refresh token 已过绝对有效期");
        }
        redisTemplate.opsForValue().set(tokenKey(newId), newInfo, ttl, TimeUnit.SECONDS);
        // 同步更新用户索引 → 新 tokenId（之前漏了这步）
        redisTemplate.opsForValue().set(userKey(info.getUserId()), newId, ttl, TimeUnit.SECONDS);
        revokeRefreshToken(oldId);
        log.info("轮换 Refresh Token: {} → {} for user {}", oldId, newId, info.getUserId());
        return newId;
    }

    public void revokeRefreshToken(String tokenId) {
        Boolean result = redisTemplate.delete(tokenKey(tokenId));
        log.info("吊销 Refresh Token: {} — {}", tokenId, Boolean.TRUE.equals(result) ? "成功" : "key 不存在");
    }

    public RefreshTokenInfo getInfoByToken(String tokenId) {
        return (RefreshTokenInfo) redisTemplate.opsForValue().get(tokenKey(tokenId));
    }
}
