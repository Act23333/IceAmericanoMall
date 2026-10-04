package org.icedamericanomall.wallet.domain.repository;

import java.util.Optional;

/**
 * 钱包账户仓储（领域层端口，基础设施层实现，落 {@code wallet_account} 表）。
 *
 * <p>扣减/入账均为单语句原子操作：扣减带 {@code balance >= amount} 条件，
 * 入账 upsert 兜底初始化账户行（无行用户首次入账即建户）。
 */
public interface WalletAccountRepository {

    /** 查询账户（无账户行返回 empty —— 语义上即余额 0）。 */
    Optional<Integer> findBalanceByUserId(Long userId);

    /** 原子扣减（条件：余额充足），返回影响行数（0=余额不足或无账户）。 */
    int deduct(Long userId, int amount);

    /** 原子入账（upsert：无账户行时初始化为该金额），返回影响行数。 */
    int credit(Long userId, int amount);
}
