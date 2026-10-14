package org.icedamericanomall.wallet.infrastructure.persistence;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.icedamericanomall.common.utils.IdGenerator;
import org.icedamericanomall.wallet.domain.model.WalletTransaction;
import org.icedamericanomall.wallet.domain.repository.WalletTransactionRepository;
import org.springframework.stereotype.Component;

/**
 * 钱包流水仓储实现（基础设施层）—— 落 {@code wallet_transaction} 表。
 *
 * <p>PO ↔ 领域 record 的转换在本层完成（领域层不感知持久化对象）。
 * 插入由 MyBatis-Plus 填充审计列（{@code createTime}/{@code updateTime}）。
 *
 * <p><b>幂等键（ADR-050）</b>：{@code transaction_no} 为 BINARY(16)——写入方唯一（pay 退款链路
 * 复制 refund_no），入参一律 32-hex；非 hex 入参属调用方契约违约，{@link IdGenerator#toBytes}
 * fail-fast（不静默落空）。
 */
@Component
@RequiredArgsConstructor
public class WalletTransactionRepositoryImpl implements WalletTransactionRepository {

    private final WalletTransactionMapper mapper;

    @Override
    public boolean existsByTransactionNo(String transactionNo) {
        return mapper.selectCount(Wrappers.<WalletTransactionPO>lambdaQuery()
                .eq(WalletTransactionPO::getTransactionNo, IdGenerator.toBytes(transactionNo))) > 0;
    }

    @Override
    public void insert(WalletTransaction transaction) {
        WalletTransactionPO po = new WalletTransactionPO();
        po.setTransactionNo(IdGenerator.toBytes(transaction.transactionNo()));
        po.setUserId(transaction.userId());
        po.setType(transaction.type());
        po.setAmount(transaction.amount());
        po.setBizNo(transaction.bizNo());
        po.setRemark(transaction.remark());
        mapper.insert(po);
    }
}
