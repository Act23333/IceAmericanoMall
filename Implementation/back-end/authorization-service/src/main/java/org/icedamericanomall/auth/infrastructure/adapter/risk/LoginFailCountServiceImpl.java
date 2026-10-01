package org.icedamericanomall.auth.infrastructure.adapter.risk;

import lombok.RequiredArgsConstructor;
import org.icedamericanomall.auth.app.port.risk.LoginFailCountService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 登录失败计数实现（基础设施层）—— Redis 计数器，窗口 30 分钟。
 *
 * <p>失败次数的唯一数据源：风控评分与登录安全防护都读这一份计数。
 */
@Component
@RequiredArgsConstructor
public class LoginFailCountServiceImpl implements LoginFailCountService {

    /** Key 前缀：{@code risk:login:fail:{account}} */
    private static final String KEY_PREFIX = "risk:login:fail:";

    /** 计数窗口 */
    private static final Duration TTL = Duration.ofMinutes(30);

    private final StringRedisTemplate redisTemplate;

    @Override
    public int getFailCount(String account) {
        String value = redisTemplate.opsForValue().get(KEY_PREFIX + account);
        return value == null ? 0 : Integer.parseInt(value);
    }

    @Override
    public void increment(String account) {
        String key = KEY_PREFIX + account;
        redisTemplate.opsForValue().increment(key);
        redisTemplate.expire(key, TTL);
    }

    @Override
    public void clear(String account) {
        redisTemplate.delete(KEY_PREFIX + account);
    }
}
