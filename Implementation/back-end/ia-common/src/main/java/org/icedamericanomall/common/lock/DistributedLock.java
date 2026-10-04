package org.icedamericanomall.common.lock;

import java.util.function.Supplier;

/**
 * 业务键互斥能力（共享内核统一定义，基础设施层用 Redisson 实现）。
 *
 * <p>领域/应用层只表达「同一业务键需要串行执行」这一诉求，不感知 Redis / 锁实现。
 *
 * <p><b>归属（V5.4 归并）</b>：本接口与 {@link RedissonDistributedLockAdapter} 此前在
 * authorization-service 与 user-service 各存一份逐字重复的副本（{@code domain/port} +
 * {@code infrastructure/adapter}）。现统一收敛到 ia-common，由
 * {@link org.icedamericanomall.common.config.DistributedLockConfig} 条件装配
 * （类路径无 Redisson 的服务不受影响），两个服务删除各自副本。
 *
 * <p>调用方：authorization-service 的 {@code UserAuthProvisioner}、
 * user-service 的 {@code UserProvisioner}（注册/登录幂等查建互斥）。
 */
public interface DistributedLock {

    /**
     * 在业务键互斥锁保护下执行 action。
     *
     * @param lockKey 业务键（如 {@code user_auth:create:PHONE:{hash}}）
     * @param action  临界区逻辑
     * @param <T>     返回值类型
     * @return action 的返回值
     * @throws org.icedamericanomall.common.exception.BizException 获取锁失败时抛出（系统繁忙）
     */
    <T> T executeWithLock(String lockKey, Supplier<T> action);
}
