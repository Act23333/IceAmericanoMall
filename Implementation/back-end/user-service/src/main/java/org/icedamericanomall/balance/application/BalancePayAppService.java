package org.icedamericanomall.balance.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.balance.domain.Account;
import org.icedamericanomall.balance.domain.AccountRepository;
import org.noLazy.common.enums.ErrorCode;
import org.noLazy.common.exception.BizException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 余额支付应用服务 — Application 层
 *
 * <pre>
 * DDD COLA 调用链:
 *   Controller → AppService(@Transactional) → Domain Entity.pay() → Repository.atomicSQL()
 *
 * 事务边界: 放在 Application 层，保证领域行为 + 原子持久化在同一事务内。
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BalancePayAppService {

    private final AccountRepository accountRepository;

    /**
     * 余额支付。
     *
     * 1. 从仓储加载 Account 聚合根
     * 2. 充血模型: account.pay(amount) 做领域校验（余额不足抛异常，不碰DB）
     * 3. 原子持久化: repository.deductAtomically() 执行 UPDATE ... WHERE balance >= #{amount}
     *    返回0行说明并发冲突 → 抛乐观锁异常，@Transactional 回滚
     *
     * @param userId 用户ID
     * @param amount 支付金额（分）
     * @return 支付后的新余额
     */
    @Transactional(rollbackFor = Exception.class)
    public int pay(Long userId, int amount) {
        // 1. 加载聚合根
        Account account = accountRepository.findByUserId(userId);

        // 2. 充血模型校验 + 内存状态变更（无外部依赖）
        account.pay(amount);

        // 3. 原子持久化 — 乐观锁防并发
        int rows = accountRepository.deductAtomically(account, amount);
        if (rows == 0) {
            log.warn("余额支付原子扣款失败(并发或余额不足): userId={}, amount={}, balance={}",
                    userId, amount, account.getBalance());
            throw new BizException(ErrorCode.BALANCE_INSUFFICIENT, "支付失败，请刷新后重试");
        }

        int newBalance = account.getBalance(); // 已在 pay() 中扣除
        log.info("余额支付成功: userId={}, amount={}, newBalance={}", userId, amount, newBalance);
        return newBalance;
    }

    /** 查询余额（只读，无事务） */
    public int getBalance(Long userId) {
        return accountRepository.findByUserId(userId).getBalance();
    }

    /**
     * 充值。
     *
     * 1. 加载聚合根
     * 2. 内存充值 account.recharge(amount)
     * 3. 原子持久化
     */
    @Transactional(rollbackFor = Exception.class)
    public int recharge(Long userId, int amount) {
        Account account = accountRepository.findByUserId(userId);
        account.recharge(amount);
        accountRepository.creditAtomically(account, amount);
        log.info("余额充值成功: userId={}, amount={}, newBalance={}", userId, amount, account.getBalance());
        return account.getBalance();
    }
}
