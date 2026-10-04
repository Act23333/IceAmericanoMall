package org.icedamericanomall.common.lock;

import lombok.RequiredArgsConstructor;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 业务键互斥适配器 —— 用 Redisson 实现 {@link DistributedLock}。
 *
 * <p><b>装配方式</b>：本类<b>刻意不标注</b> {@code @Component}（ia-common 的包不在各服务
 * 组件扫描路径内在，扫描注解无效），由
 * {@link org.icedamericanomall.common.config.DistributedLockConfig} 以
 * {@code @ConditionalOnClass(RedissonClient.class)} 条件装配为 Bean：
 * 类路径无 Redisson 的服务不受影响。
 *
 * <p>参数（等待 3s / 租期 5s）为 authorization-service 与 user-service 两个副本验证过的取值。
 */
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
