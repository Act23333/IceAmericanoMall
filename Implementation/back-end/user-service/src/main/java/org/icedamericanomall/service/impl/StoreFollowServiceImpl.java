package org.icedamericanomall.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.service.NotificationService;
import org.icedamericanomall.service.StoreFollowService;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 店铺关注服务实现 — Redis Set 存储，异步同步 MySQL (V5.0+)
 *
 * Controller 不得直接操作 Redis — 全部下沉到此 Infrastructure 实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StoreFollowServiceImpl implements StoreFollowService {

    private final RedisTemplate<String, String> redisTemplate;
    private final NotificationService notificationService;
    private static final String FOLLOW_KEY = "shop:followers:";
    private static final String USER_FOLLOW_KEY = "user:follow:shops:";

    @Override
    public long follow(Long userId, Long sellerId) {
        String shopKey = FOLLOW_KEY + sellerId;
        redisTemplate.opsForSet().add(shopKey, userId.toString());
        redisTemplate.opsForSet().add(USER_FOLLOW_KEY + userId, sellerId.toString());
        Long count = redisTemplate.opsForSet().size(shopKey);
        // 通知店铺主: 有人关注了你的店铺
        try {
            notificationService.send(sellerId, userId, null, null,
                    "FOLLOW", "有人关注了你的店铺", null,
                    "/shop/" + sellerId);
        } catch (Exception ignored) {}
        log.debug("用户关注店铺: userId={}, sellerId={}, count={}", userId, sellerId, count);
        return count != null ? count : 0;
    }

    @Override
    public void unfollow(Long userId, Long sellerId) {
        redisTemplate.opsForSet().remove(FOLLOW_KEY + sellerId, userId.toString());
        redisTemplate.opsForSet().remove(USER_FOLLOW_KEY + userId, sellerId.toString());
    }

    @Override
    public boolean isFollowing(Long userId, Long sellerId) {
        return Boolean.TRUE.equals(
                redisTemplate.opsForSet().isMember(USER_FOLLOW_KEY + userId, sellerId.toString()));
    }

    @Override
    public long followerCount(Long sellerId) {
        Long count = redisTemplate.opsForSet().size(FOLLOW_KEY + sellerId);
        return count != null ? count : 0;
    }
}
