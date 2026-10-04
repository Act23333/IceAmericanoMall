package org.icedamericanomall.wallet.domain.repository;

import org.icedamericanomall.wallet.domain.model.WalletTransaction;

/**
 * 钱包流水仓储（领域层端口，基础设施层实现，落 {@code wallet_transaction} 表）。
 *
 * <p>只按<b>业务唯一键</b> {@code transaction_no} 操作，不暴露技术主键——流水是不可变事实数据，
 * 没有更新语义，故接口只有「存在性判定」与「插入」两个方法（幂等判定 + 落账）。
 */
public interface WalletTransactionRepository {

    /**
     * 按流水号判断是否已入账（幂等前置判定）。
     *
     * @param transactionNo 流水号（业务唯一键）
     * @return true = 该流水已存在（重复请求，应幂等返回成功）
     */
    boolean existsByTransactionNo(String transactionNo);

    /**
     * 插入一条流水。
     *
     * <p>{@code transaction_no} 唯一约束是并发下的最终兜底：重复插入抛
     * {@code DuplicateKeyException}，由调用方（{@code WalletManager.refund}）回滚整个本地事务，
     * 保证「不重复加余额」。
     *
     * @param transaction 流水
     */
    void insert(WalletTransaction transaction);
}
