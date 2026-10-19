package org.icedamericanomall.trade.domain.repository;

import org.icedamericanomall.trade.domain.service.SettlementAllocator;

import java.util.List;

/**
 * 提现核销仓储（领域端口）—— 实现方：{@code infrastructure.persistence.finance.WithdrawalSettlementRepositoryImpl}。
 *
 * <p>核销把一笔提现**按金额**摊到若干结算单上：明细行是<b>真源</b>，
 * {@code settlement.withdrawn_amount} 是它的聚合投影（只能由本仓储的两个方法改）。
 *
 * <p><b>两个方法都必须是「要么全成、要么全败」</b>：它们各自是多语句写
 * （明细插入/删除 + 结算单列更新），事务归属落在实现类（REQUIRED，加入调用方事务）。
 */
public interface WithdrawalSettlementRepository {

    /**
     * 核销：按计划逐行插入明细，并把每张结算单的 {@code withdrawn_amount} 累加。
     *
     * @param withdrawalId 提现单技术主键（必须已落库，明细要引用它）
     * @param plan         FIFO 规划结果；调用方必须已校验 {@code Σ amount == 提现金额}
     * @throws org.icedamericanomall.common.exception.BizException 任一张单可核销额不足 ⇒ 整笔回滚
     */
    void allocate(Long withdrawalId, List<SettlementAllocator.Allocation> plan);

    /**
     * 释放（审核拒绝）：删该提现的全部明细，并按明细逐行回退 {@code withdrawn_amount}。
     *
     * <p>先取明细再删 —— 删完就查不到「该退多少」。任一行回退 0 行 ⇒ 账不平 ⇒ 抛异常整笔回滚。
     *
     * @return 删除的明细行数（正常 &ge; 1；0 表示本来就没有核销，属数据异常，调用方自行决定是否报警）
     */
    int release(Long withdrawalId);
}
