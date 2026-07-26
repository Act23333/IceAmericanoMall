package org.icedamericanomall.controller;

import lombok.RequiredArgsConstructor;
import org.noLazy.common.domain.Result;
import org.noLazy.common.utils.UserContext;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.*;

/**
 * V3.5: 关注/取消关注商家（京东标准 — 店铺关注）。
 * 使用 Redis Set 存储关注关系，异步同步到 MySQL store_follow 表。
 */
@RestController
@RequestMapping("/api/shop")
@RequiredArgsConstructor
public class StoreFollowController {

    private final RedisTemplate<String, String> redisTemplate;
    private static final String FOLLOW_KEY = "shop:followers:";
    private static final String USER_FOLLOW_KEY = "user:follow:shops:";

    @PostMapping("/follow/{sellerId}")
    public Result<Boolean> follow(@PathVariable Long sellerId) {
        Long userId = UserContext.getUserId();
        if (userId == null) return Result.error(401, "请先登录");

        String key = FOLLOW_KEY + sellerId;
        redisTemplate.opsForSet().add(key, userId.toString());
        redisTemplate.opsForSet().add(USER_FOLLOW_KEY + userId, sellerId.toString());

        long count = redisTemplate.opsForSet().size(key);
        return Result.ok(true);
    }

    @DeleteMapping("/follow/{sellerId}")
    public Result<Boolean> unfollow(@PathVariable Long sellerId) {
        Long userId = UserContext.getUserId();
        if (userId == null) return Result.error(401, "请先登录");

        redisTemplate.opsForSet().remove(FOLLOW_KEY + sellerId, userId.toString());
        redisTemplate.opsForSet().remove(USER_FOLLOW_KEY + userId, sellerId.toString());
        return Result.ok(false);
    }

    @GetMapping("/follow/{sellerId}/status")
    public Result<Boolean> isFollowing(@PathVariable Long sellerId) {
        Long userId = UserContext.getUserId();
        if (userId == null) return Result.ok(false);
        return Result.ok(Boolean.TRUE.equals(
                redisTemplate.opsForSet().isMember(USER_FOLLOW_KEY + userId, sellerId.toString())));
    }

    @GetMapping("/follow/{sellerId}/count")
    public Result<Long> followerCount(@PathVariable Long sellerId) {
        return Result.ok(redisTemplate.opsForSet().size(FOLLOW_KEY + sellerId));
    }
}
