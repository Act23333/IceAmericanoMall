package org.icedamericanomall.common.lock;

import org.icedamericanomall.common.config.DistributedLockConfig;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 业务键互斥锁（V5.4 归并到 ia-common）单元测试。
 *
 * <p>锁语义不变量：拿到锁 → 执行临界区并解锁；拿不到 → {@code SYSTEM_BUSY} 业务异常
 * （绝不静默放过临界区）；中断 → 复位中断标志 + {@code INTERNAL_ERROR}。
 */
@DisplayName("RedissonDistributedLockAdapter — 业务键互斥语义")
@ExtendWith(MockitoExtension.class)
class RedissonDistributedLockAdapterTest {

    private static final String LOCK_KEY = "user_auth:create:PHONE:hash";

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RLock lock;

    /** 走 ia-common 的条件装配入口（new 出配置类调用 Bean 方法，等价于容器装配结果）。 */
    private DistributedLock newDistributedLock() {
        return new DistributedLockConfig().distributedLock(redissonClient);
    }

    @Test
    @DisplayName("Config 装配 → 返回 Redisson 实现（@ConditionalOnClass 守卫的 Bean 定义）")
    void shouldAssembleRedissonAdapter() {
        assertThat(newDistributedLock()).isInstanceOf(RedissonDistributedLockAdapter.class);
    }

    @Test
    @DisplayName("获取锁成功 → 执行临界区、返回结果并释放锁")
    void shouldExecuteAction_whenLockAcquired() throws InterruptedException {
        when(redissonClient.getLock(LOCK_KEY)).thenReturn(lock);
        when(lock.tryLock(anyLong(), anyLong(), eq(TimeUnit.SECONDS))).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);

        String result = newDistributedLock().executeWithLock(LOCK_KEY, () -> "created");

        assertThat(result).isEqualTo("created");
        verify(lock).unlock();
    }

    @Test
    @DisplayName("获取锁失败 → BizException(SYSTEM_BUSY)，临界区不执行")
    void shouldThrowSystemBusy_whenLockNotAcquired() throws InterruptedException {
        when(redissonClient.getLock(LOCK_KEY)).thenReturn(lock);
        when(lock.tryLock(anyLong(), anyLong(), eq(TimeUnit.SECONDS))).thenReturn(false);

        assertThatThrownBy(() -> newDistributedLock().executeWithLock(LOCK_KEY, () -> "never"))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getErrorCode())
                .isEqualTo(ErrorCode.SYSTEM_BUSY);

        verify(lock, never()).unlock();
    }

    @Test
    @DisplayName("等待锁被中断 → INTERNAL_ERROR 且复位中断标志")
    void shouldRestoreInterruptFlag_whenInterrupted() throws InterruptedException {
        when(redissonClient.getLock(LOCK_KEY)).thenReturn(lock);
        when(lock.tryLock(anyLong(), anyLong(), eq(TimeUnit.SECONDS)))
                .thenThrow(new InterruptedException("interrupted"));

        assertThatThrownBy(() -> newDistributedLock().executeWithLock(LOCK_KEY, () -> "never"))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getErrorCode())
                .isEqualTo(ErrorCode.INTERNAL_ERROR);

        assertThat(Thread.currentThread().isInterrupted()).isTrue();
        // 复位（避免污染后续测试线程）
        Thread.interrupted();
    }
}
