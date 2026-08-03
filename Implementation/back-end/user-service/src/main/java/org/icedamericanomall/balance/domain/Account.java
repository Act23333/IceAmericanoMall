package org.icedamericanomall.balance.domain;

import lombok.Getter;
import org.noLazy.common.enums.ErrorCode;
import org.noLazy.common.exception.BizException;

import java.time.LocalDateTime;

/**
 * 账户 — 余额支付限界上下文的充血聚合根
 *
 * <pre>
 * 不变量:
 *   1. balance >= 0（永远不为负）
 *   2. 扣款时 balance >= amount，否则抛出 INSUFFICIENT_BALANCE
 *
 * 设计约束（DDD 铁律）:
 *   - 不依赖任何外部服务/基础设施（无 Spring 注解、无 Mapper、无 Feign）
 *   - 只做自身状态校验与变更
 *   - 持久化由 {@link AccountRepository} 负责
 * </pre>
 */
@Getter
public class Account {

    private Long id;
    private Long userId;
    private Integer balance; // 单位：分
    private Integer version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    // ==================== 工厂方法 ====================

    /** 从持久层重建（Repository 专用） */
    public static Account reconstruct(Long id, Long userId, Integer balance,
                                       Integer version, LocalDateTime createTime, LocalDateTime updateTime) {
        Account a = new Account();
        a.id = id;
        a.userId = userId;
        a.balance = balance;
        a.version = version;
        a.createTime = createTime;
        a.updateTime = updateTime;
        return a;
    }

    // ==================== 领域行为 ====================

    /**
     * 余额支付扣款。
     *
     * @param amount 扣款金额（分）
     * @return 扣款后的新余额
     * @throws BizException 余额不足
     */
    public int pay(int amount) {
        if (amount <= 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "支付金额必须大于0");
        }
        if (this.balance < amount) {
            throw new BizException(ErrorCode.BALANCE_INSUFFICIENT,
                    String.format("余额不足: 需要 %d 分, 当前 %d 分", amount, this.balance));
        }
        this.balance -= amount;
        return this.balance;
    }

    /**
     * 充值。
     *
     * @param amount 充值金额（分）
     * @return 充值后的新余额
     */
    public int recharge(int amount) {
        if (amount <= 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "充值金额必须大于0");
        }
        this.balance += amount;
        return this.balance;
    }

    /** 账户是否余额充足 */
    public boolean hasSufficientBalance(int amount) {
        return this.balance >= amount;
    }
}
