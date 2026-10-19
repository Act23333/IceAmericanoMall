package org.icedamericanomall.trade.infrastructure.persistence.finance;

import lombok.RequiredArgsConstructor;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.trade.domain.enums.SettlementStatusEnum;
import org.icedamericanomall.trade.domain.repository.WithdrawalSettlementRepository;
import org.icedamericanomall.trade.domain.service.SettlementAllocator;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 核销仓储实现：明细表 + 结算单列（{@code withdrawn_amount}）的**唯一**写入者。
 *
 * <p>两个方法都是多语句本地写，事务落在这里（REQUIRED，加入调用方事务）——
 * 与 {@code SellerFundAccountRepositoryImpl} 同一套理由：跨表写必须与调用方的业务动作同一个事务。
 *
 * <p><b>顺序不可换</b>：
 * <ul>
 *   <li>核销：<b>先条件累加结算单列，再插明细</b>。反过来先插明细，一旦累加 0 行（可核销额不足）
 *       就得多写一段"删掉刚插的明细"的补偿 —— 而在同一事务里回滚本来就会删掉，
 *       所以先做**可能失败**的那一步，代码更短也更难写错。</li>
 *   <li>释放：<b>先取明细、再逐行回退、最后删明细</b>。删完就查不到该退多少。</li>
 * </ul>
 */
@Repository
@RequiredArgsConstructor
public class WithdrawalSettlementRepositoryImpl implements WithdrawalSettlementRepository {

    private final WithdrawalSettlementMapper withdrawalSettlementMapper;
    private final SettlementMapper settlementMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void allocate(Long withdrawalId, List<SettlementAllocator.Allocation> plan) {
        for (SettlementAllocator.Allocation allocation : plan) {
            int updated = settlementMapper.increaseWithdrawn(allocation.settlementId(),
                    SettlementStatusEnum.SETTLED.getCode(), allocation.amount());
            if (updated == 0) {
                // 失败关闭：核销必须足额，少核销会让 E5（Σ 明细 == 提现金额）立刻破
                throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION,
                        "核销失败：结算单可核销额不足（settlementId=" + allocation.settlementId()
                                + ", amount=" + allocation.amount() + "）");
            }
            WithdrawalSettlementPO po = new WithdrawalSettlementPO();
            po.setWithdrawalId(withdrawalId);
            po.setSettlementId(allocation.settlementId());
            po.setAmount(allocation.amount());
            withdrawalSettlementMapper.insert(po);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int release(Long withdrawalId) {
        List<WithdrawalSettlementPO> rows = withdrawalSettlementMapper.selectByWithdrawalId(withdrawalId);
        for (WithdrawalSettlementPO row : rows) {
            int reverted = settlementMapper.decreaseWithdrawn(row.getSettlementId(),
                    row.getAmount() == null ? 0 : row.getAmount());
            if (reverted == 0) {
                throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION,
                        "核销释放失败：结算单 withdrawn_amount 不足以回退（settlementId="
                                + row.getSettlementId() + "）");
            }
        }
        if (!rows.isEmpty()) {
            withdrawalSettlementMapper.deleteByWithdrawalId(withdrawalId);
        }
        return rows.size();
    }
}
