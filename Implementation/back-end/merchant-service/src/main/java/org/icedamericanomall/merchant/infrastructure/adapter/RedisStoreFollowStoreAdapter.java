package org.icedamericanomall.merchant.infrastructure.adapter;

import lombok.RequiredArgsConstructor;
import org.icedamericanomall.merchant.app.port.StoreFollowStore;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 店铺关注存储适配器（Redis Set 实现）。
 *
 * <p>Key 布局（模块内私有）：{@code shop:followers:{shopId}} +
 * {@code merchant:follow:shops:{userId}}；写操作天然幂等（Set 去重）。
 * V5.4 起关注对象由 sellerId 改为 shopId（开发期数据可弃，见 {@link StoreFollowStore}）。
 *
 * <p><b>批量读取走 pipeline</b>：{@code followerCounts} 把 N 个 {@code SCARD} 压进<b>一次</b>
 * 往返（{@code executePipelined}），店铺列表页不会因店铺数增长退化成 N 次网络往返。
 * 结果按命令下发顺序与店铺 ID 一一对应（pipeline 保序）。
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

    /**
     * 批量取粉丝数 —— 单次 pipeline（一次往返）下发 N 个 {@code SCARD}。
     *
     * <p>实现<b>不吞异常</b>：Redis 不可用/键类型错误时原样上抛，由应用层
     * （{@code ShopManager}）按 ADR-057 fail-open 处理（粉丝数留 null，店铺照常返回）。
     */
    @Override
    public Map<Long, Long> followerCounts(Collection<Long> shopIds) {
        List<Long> ids = normalize(shopIds);
        if (ids.isEmpty()) {
            return Map.of();
        }
        List<Object> counts = redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            for (Long shopId : ids) {
                connection.setCommands().sCard(rawKey(FOLLOW_KEY + shopId));
            }
            // 回调返回 null：结果集由 pipeline 收集返回（顺序 = 命令下发顺序）
            return null;
        });
        Map<Long, Long> result = new LinkedHashMap<>(ids.size());
        for (int i = 0; i < ids.size(); i++) {
            Object value = counts != null && i < counts.size() ? counts.get(i) : null;
            result.put(ids.get(i), value instanceof Number number ? number.longValue() : 0L);
        }
        return result;
    }

    /** StringRedisTemplate 的键序列化为 UTF-8 字符串，原生命令需同口径字节。 */
    private byte[] rawKey(String key) {
        return key.getBytes(StandardCharsets.UTF_8);
    }

    private List<Long> normalize(Collection<Long> shopIds) {
        if (shopIds == null || shopIds.isEmpty()) {
            return List.of();
        }
        return shopIds.stream().filter(Objects::nonNull).distinct().toList();
    }
}
