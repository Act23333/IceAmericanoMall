package org.icedamericanomall.balance.domain;

/**
 * 账户仓储接口 — 领域层定义契约，基础设施层实现
 *
 * <pre>
 * DDD 铁律: 接口在 domain 层，实现在 infrastructure 层。
 * 领域层不依赖任何 ORM/DB 框架。
 * </pre>
 */
public interface AccountRepository {

    /** 按用户ID查找账户 */
    Account findByUserId(Long userId);

    /**
     * 原子扣款（乐观锁）。
     *
     * SQL: UPDATE user SET balance = balance - #{amount}, version = version + 1
     *      WHERE id = #{id} AND balance >= #{amount}
     *
     * @param account 聚合根
     * @param amount  扣款金额（分）
     * @return 影响行数（1=成功，0=余额不足/版本冲突）
     */
    int deductAtomically(Account account, int amount);

    /**
     * 原子充值。
     *
     * SQL: UPDATE user SET balance = balance + #{amount}, version = version + 1
     *      WHERE id = #{id}
     *
     * @param account 聚合根
     * @param amount  充值金额（分）
     * @return 影响行数
     */
    int creditAtomically(Account account, int amount);
}
