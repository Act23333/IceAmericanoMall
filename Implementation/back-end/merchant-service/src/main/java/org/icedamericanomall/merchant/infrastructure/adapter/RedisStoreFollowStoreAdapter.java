package org.icedamericanomall.merchant.infrastructure.adapter;

import lombok.RequiredArgsConstructor;
import org.icedamericanomall.merchant.app.port.StoreFollowStore;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 店铺关注存储适配器（Redis Set 实现）。
 *
 * <p>Key 布局（模块内私有）：{@code shop:followers:{shopId}} +
 * {@code merchant:follow:shops:{userId}}；写操作天然幂等（Set 去重）。
 * V5.4 起关注对象由 sellerId 改为 shopId（开发期数据可弃，见 {@link StoreFollowStore}）。
 */
@Component
@RequiredArgsConstructor
public class RedisStoreFollowStoreAdapter implements StoreFollowStore {

    private static final String FOLLOW_KEY = "shop:followers:";
    private static final String USER_FOLLOW_KEY = "merchant:follow:shops:";

    private final StringRedisTemplate redisTemplate;

    @Override
    public long follow(Long userId, Long shopId) {
        redisTemplate.opsForSet().add(FOLLOW_KEY + shopId, userId.toString());
        redisTemplate.opsForSet().add(USER_FOLLOW_KEY + userId, shopId.toString());
        Long count = redisTemplate.opsForSet().size(FOLLOW_KEY + shopId);
        return count != null ? count : 0;
    }

    @Override
    public void unfollow(Long userId, Long shopId) {
        redisTemplate.opsForSet().remove(FOLLOW_KEY + shopId, userId.toString());
        redisTemplate.opsForSet().remove(USER_FOLLOW_KEY + userId, shopId.toString());
    }

    @Override
    public boolean isFollowing(Long userId, Long shopId) {
        return Boolean.TRUE.equals(
                redisTemplate.opsForSet().isMember(USER_FOLLOW_KEY + userId, shopId.toString()));
    }

    @Override
    public long followerCount(Long shopId) {
        Long count = redisTemplate.opsForSet().size(FOLLOW_KEY + shopId);
        return count != null ? count : 0;
    }
}
