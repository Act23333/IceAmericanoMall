package org.icedAmericanoMall.controller;

import lombok.RequiredArgsConstructor;
import org.noLazy.common.domain.Result;
import org.noLazy.common.utils.UserContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 商品浏览历史 — Redis List 存储（最多保留 100 条）。
 */
@RestController
@RequestMapping("/user/history")
@RequiredArgsConstructor
public class HistoryController {

    private final StringRedisTemplate redisTemplate;

    @PostMapping
    public Result<?> record(@RequestParam Long productId) {
        Long userId = UserContext.getUser();
        String key = "user:history:" + userId;
        // Remove existing entry for dedup, then push to front
        redisTemplate.opsForList().remove(key, 0, productId.toString());
        redisTemplate.opsForList().leftPush(key, productId.toString());
        // Keep only latest 100
        redisTemplate.opsForList().trim(key, 0, 99);
        return Result.ok();
    }

    @GetMapping
    public Result<List<Long>> list(@RequestParam(defaultValue = "20") int size) {
        Long userId = UserContext.getUser();
        String key = "user:history:" + userId;
        List<String> ids = redisTemplate.opsForList().range(key, 0, size - 1);
        if (ids == null) return Result.ok(Collections.emptyList());
        List<Long> productIds = ids.stream().map(Long::valueOf).toList();
        return Result.ok(productIds);
    }

    @DeleteMapping
    public Result<?> clear() {
        Long userId = UserContext.getUser();
        redisTemplate.delete("user:history:" + userId);
        return Result.ok();
    }
}
