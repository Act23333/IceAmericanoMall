package org.icedamericanomall.service.impl;

import lombok.RequiredArgsConstructor;
import org.icedamericanomall.service.HistoryService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class HistoryServiceImpl implements HistoryService {

    private final StringRedisTemplate redisTemplate;

    @Override
    public void record(Long userId, Long productId) {
        String key = "user:history:" + userId;
        redisTemplate.opsForList().remove(key, 0, productId.toString());
        redisTemplate.opsForList().leftPush(key, productId.toString());
        redisTemplate.opsForList().trim(key, 0, 99);
    }

    @Override
    public List<Long> list(Long userId, int size) {
        List<String> ids = redisTemplate.opsForList().range("user:history:" + userId, 0, size - 1);
        if (ids == null) return Collections.emptyList();
        return ids.stream().map(Long::valueOf).toList();
    }

    @Override
    public void clear(Long userId) {
        redisTemplate.delete("user:history:" + userId);
    }
}
