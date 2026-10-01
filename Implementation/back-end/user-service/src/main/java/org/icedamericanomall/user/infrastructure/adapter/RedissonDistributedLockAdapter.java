package org.icedamericanomall.user.infrastructure.adapter;

import lombok.RequiredArgsConstructor;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.user.domain.port.DistributedLock;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 业务键互斥适配器 —— 用 Redisson 实现领域端口 {@link DistributedLock}。
 *
 * <p>与 authorization-service 的 {@code RedissonDistributedLockAdapter} 保持一致的契约
 * （等待 3s / 租期 5s / 不可重入解锁校验），严格复制已验证模式。
 */
@Component
@RequiredArgsConstructor
public class RedissonDistributedLockAdapter implements DistributedLock {

    /** 获取锁的最长等待时间（秒） */
    private static final long WAIT_SECONDS = 3;

    /** 锁租期（秒），自动释放，避免业务异常导致死锁 */
    private static final long LEASE_SECONDS = 5;

    private final RedissonClient redissonClient;

    @Override
    public <T> T executeWithLock(String lockKey, Supplier<T> action) {
        RLock lock = redissonClient.getLock(lockKey);
        boolean locked = false;
        try {
            locked = lock.tryLock(WAIT_SECONDS, LEASE_SECONDS, TimeUnit.SECONDS);
            if (!locked) {
                throw new BizException(ErrorCode.SYSTEM_BUSY, "系统繁忙，请重试");
            }
            return action.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BizException(ErrorCode.INTERNAL_ERROR, "系统异常");
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
