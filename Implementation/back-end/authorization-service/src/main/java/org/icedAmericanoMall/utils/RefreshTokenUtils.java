package org.icedAmericanoMall.utils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.icedAmericanoMall.constants.RedisKeyConstants;
import org.icedAmericanoMall.domain.dto.RefreshTokenInfo;
import org.noLazy.common.enums.ErrorCode;
import org.noLazy.common.exception.UnauthorizedException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenUtils {

    private final RedisTemplate<String, Object> redisTemplate;

    public String createRefreshToken(Long userId, String username, long ttlSeconds) {
        long now = System.currentTimeMillis();
        long maxExpireAt = now + ttlSeconds * 1000;
        String tokenId = UUID.randomUUID().toString().replace("-", "");
        RefreshTokenInfo info = new RefreshTokenInfo(tokenId, userId, username,
                now, maxExpireAt);
        String key = RedisKeyConstants.REFRESH_TOKEN_PREFIX + tokenId;
        redisTemplate.opsForValue().set(key, info, ttlSeconds, TimeUnit.SECONDS);
        log.info("创建 Refresh Token: {} for user {}", tokenId, userId);
        return tokenId;
    }

    public String rotateRefreshToken(String tokenId) {
        String key = RedisKeyConstants.REFRESH_TOKEN_PREFIX + tokenId;
        RefreshTokenInfo info = (RefreshTokenInfo) redisTemplate.opsForValue().get(key);
        if (info == null) {
            throw new UnauthorizedException(ErrorCode.TOKEN_EXPIRED, "Refresh Token 无效或已过期");
        }
        if (info.getMaxExpireAt() < System.currentTimeMillis()) {
            revokeRefreshToken(tokenId);
            throw new UnauthorizedException(ErrorCode.TOKEN_EXPIRED, "Refresh Token 已过期");
        }
        String newTokenId = UUID.randomUUID().toString().replace("-", "");
        RefreshTokenInfo newInfo = new RefreshTokenInfo(
                newTokenId,
                info.getUserId(),
                info.getUsername(),
                System.currentTimeMillis(),
                info.getMaxExpireAt()
        );
        long ttl = (newInfo.getMaxExpireAt() - System.currentTimeMillis()) / 1000;
        if (ttl <= 0) {
            throw new UnauthorizedException(ErrorCode.TOKEN_EXPIRED, "Refresh token 已过绝对有效期");
        }
        String newKey = RedisKeyConstants.REFRESH_TOKEN_PREFIX + newTokenId;
        redisTemplate.opsForValue().set(newKey, newInfo, ttl, TimeUnit.SECONDS);
        // Revoke old token after successful rotation
        revokeRefreshToken(tokenId);
        return newTokenId;
    }

    public void revokeRefreshToken(String tokenId) {
        String redisKey = RedisKeyConstants.REFRESH_TOKEN_PREFIX + tokenId;
        Boolean result = redisTemplate.delete(redisKey);
        if (Boolean.FALSE.equals(result)) {
            log.info("吊销失败 Refresh Token: {}，redisKey: {}", tokenId, redisKey);
        } else {
            log.info("吊销成功 Refresh Token: {}，redisKey: {}", tokenId, redisKey);
        }
    }

    public RefreshTokenInfo getInfoByToken(String tokenId) {
        String key = RedisKeyConstants.REFRESH_TOKEN_PREFIX + tokenId;
        return (RefreshTokenInfo) redisTemplate.opsForValue().get(key);
    }
}
