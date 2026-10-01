package org.icedamericanomall.user.domain.port;

import java.util.function.Supplier;

/**
 * 业务键互斥能力（领域层定义，基础设施层用 Redisson 实现）。
 *
 * <p>领域只表达"同一业务键需要串行执行"这一诉求，不感知 Redis / 锁实现。
 * 与 authorization-service 的同名端口保持一致的契约（严格复制已验证模式）。
 */
public interface DistributedLock {

    /**
     * 在业务键互斥锁保护下执行 action。
     *
     * @param lockKey 业务键（如 {@code user:create:PHONE:138xxxx}）
     * @param action  临界区逻辑
     * @return action 的返回值
     * @throws org.icedamericanomall.common.exception.BizException 获取锁失败时抛出（系统繁忙）
     */
    <T> T executeWithLock(String lockKey, Supplier<T> action);
}
