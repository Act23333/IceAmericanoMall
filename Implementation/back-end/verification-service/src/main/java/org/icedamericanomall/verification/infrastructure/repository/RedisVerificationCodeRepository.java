package org.icedamericanomall.verification.infrastructure.repository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.verification.domain.enums.VerificationChannel;
import org.icedamericanomall.verification.domain.repository.VerificationCodeRepository;
import org.icedamericanomall.verification.infrastructure.constant.VerificationRedisKeys;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.List;

/**
 * 验证码仓储实现（Redis）—— 所有状态都在 Redis，无库表。
 *
 * <p>原子性保障：
 * <ul>
 *   <li>{@link #consumeIfMatches} —— Lua 脚本（GET+DEL 原子），防重放 / 并发重放</li>
 *   <li>{@link #tryAcquireSendQuota} —— {@code SET NX EX}，防并发双发</li>
 *   <li>{@link #incrementFailures} —— {@code INCR} + 首次设置 TTL，防并发丢计数</li>
 * </ul>
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class RedisVerificationCodeRepository implements VerificationCodeRepository {

    /** 原子比对并消费脚本（classpath:lua/consume_verification_code.lua） */
    private static final RedisScript<Long> CONSUME_IF_MATCH_SCRIPT = buildScript();

    /** 限流占位值（存在即限流） */
    private static final String QUOTA_OCCUPIED = "1";

    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public void save(VerificationChannel channel, String scene, String target, String code, Duration ttl) {
        stringRedisTemplate.opsForValue().set(VerificationRedisKeys.codeKey(channel, scene, target), code, ttl);
    }

    @Override
    public boolean consumeIfMatches(VerificationChannel channel, String scene, String target, String code) {
        String key = VerificationRedisKeys.codeKey(channel, scene, target);
        Long consumed = stringRedisTemplate.execute(CONSUME_IF_MATCH_SCRIPT, List.of(key), code);
        return consumed != null && consumed == 1L;
    }

    @Override
    public boolean exists(VerificationChannel channel, String scene, String target) {
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(VerificationRedisKeys.codeKey(channel, scene, target)));
    }

    @Override
    public void removeCode(VerificationChannel channel, String scene, String target) {
        stringRedisTemplate.delete(VerificationRedisKeys.codeKey(channel, scene, target));
    }

    @Override
    public boolean tryAcquireSendQuota(VerificationChannel channel, String scene, String target, Duration window) {
        String key = VerificationRedisKeys.limitKey(channel, scene, target);
        return Boolean.TRUE.equals(stringRedisTemplate.opsForValue().setIfAbsent(key, QUOTA_OCCUPIED, window));
    }

    @Override
    public void releaseSendQuota(VerificationChannel channel, String scene, String target) {
        stringRedisTemplate.delete(VerificationRedisKeys.limitKey(channel, scene, target));
    }

    @Override
    public int incrementFailures(VerificationChannel channel, String scene, String target, Duration window) {
        String key = VerificationRedisKeys.failureKey(channel, scene, target);
        Long failures = stringRedisTemplate.opsForValue().increment(key);
        if (failures != null && failures == 1L) {
            // 首次计数才设置过期，保证「30 分钟窗口」语义不被每次 INCR 重置
            stringRedisTemplate.expire(key, window);
        }
        return failures == null ? 0 : failures.intValue();
    }

    @Override
    public void clearFailures(VerificationChannel channel, String scene, String target) {
        stringRedisTemplate.delete(VerificationRedisKeys.failureKey(channel, scene, target));
    }

    @Override
    public int incrementDailySendCount(VerificationChannel channel, String target, Duration window) {
        String key = VerificationRedisKeys.dailyKey(channel, target);
        Long count = stringRedisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            // 首次计数才设置过期，保证「滚动窗口」语义不被每次 INCR 重置
            stringRedisTemplate.expire(key, window);
        }
        return count == null ? 0 : count.intValue();
    }

    @Override
    public void decrementDailySendCount(VerificationChannel channel, String target) {
        String key = VerificationRedisKeys.dailyKey(channel, target);
        Long remaining = stringRedisTemplate.opsForValue().decrement(key);
        if (remaining != null && remaining <= 0) {
            // 回补到零即清理（防负值残留与过期语义混淆）
            stringRedisTemplate.delete(key);
        }
    }

    private static RedisScript<Long> buildScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptSource(new ResourceScriptSource(
                new ClassPathResource("lua/consume_verification_code.lua")));
        script.setResultType(Long.class);
        return script;
    }
}
