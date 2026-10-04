package org.icedamericanomall.trade.infrastructure.persistence.finance;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.icedamericanomall.trade.domain.model.finance.Settlement;
import org.icedamericanomall.trade.domain.repository.SettlementRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 商家结算单仓储实现 —— PO 与聚合互转在本类完成。
 */
@Repository
@RequiredArgsConstructor
public class SettlementRepositoryImpl implements SettlementRepository {

    private final SettlementMapper settlementMapper;

    @Override
    public Settlement save(Settlement settlement) {
        SettlementPO po = toPO(settlement);
        settlementMapper.insert(po);
        settlement.setId(po.getId());
        return settlement;
    }

    @Override
    public Settlement findById(Long id) {
        return toDomain(settlementMapper.selectById(id));
    }

    @Override
    public IPage<Settlement> pageBySeller(Long sellerId, int page, int size) {
        return settlementMapper.selectPage(new Page<>(page, size),
                        new LambdaQueryWrapper<SettlementPO>()
                                .eq(SettlementPO::getSellerId, sellerId)
                                .orderByDesc(SettlementPO::getCreateTime))
                .convert(SettlementRepositoryImpl::toDomain);
    }

    @Override
    public IPage<Settlement> pageAll(int page, int size) {
        return settlementMapper.selectPage(new Page<>(page, size),
                        new LambdaQueryWrapper<SettlementPO>().orderByDesc(SettlementPO::getCreateTime))
                .convert(SettlementRepositoryImpl::toDomain);
    }

    @Override
    public List<Settlement> findBySellerAndStatus(Long sellerId, Integer status) {
        return settlementMapper.selectList(new LambdaQueryWrapper<SettlementPO>()
                        .eq(SettlementPO::getSellerId, sellerId)
                        .eq(SettlementPO::getStatus, status))
                .stream().map(SettlementRepositoryImpl::toDomain).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatusBySellerAndStatus(Long sellerId, Integer fromStatus, Integer toStatus) {
        settlementMapper.update(null, new LambdaUpdateWrapper<SettlementPO>()
                .eq(SettlementPO::getSellerId, sellerId)
                .eq(SettlementPO::getStatus, fromStatus)
                .set(SettlementPO::getStatus, toStatus));
    }

    // ==================== PO <-> 聚合 ====================

    static Settlement toDomain(SettlementPO po) {
        if (po == null) {
            return null;
        }
        Settlement settlement = new Settlement();
        settlement.setId(po.getId());
        settlement.setSettlementNo(po.getSettlementNo());
        settlement.setSellerId(po.getSellerId());
        settlement.setPeriodStart(po.getPeriodStart());
        settlement.setPeriodEnd(po.getPeriodEnd());
        settlement.setOrderCount(po.getOrderCount());
        settlement.setTotalAmount(po.getTotalAmount());
        settlement.setCommission(po.getCommission());
        settlement.setSettlementAmount(po.getSettlementAmount());
        settlement.setStatus(po.getStatus());
        settlement.setCreateTime(po.getCreateTime());
        settlement.setUpdateTime(po.getUpdateTime());
        return settlement;
    }

    static SettlementPO toPO(Settlement settlement) {
        SettlementPO po = new SettlementPO();
        po.setId(settlement.getId());
        po.setSettlementNo(settlement.getSettlementNo());
        po.setSellerId(settlement.getSellerId());
        po.setPeriodStart(settlement.getPeriodStart());
        po.setPeriodEnd(settlement.getPeriodEnd());
        po.setOrderCount(settlement.getOrderCount());
        po.setTotalAmount(settlement.getTotalAmount());
        po.setCommission(settlement.getCommission());
        po.setSettlementAmount(settlement.getSettlementAmount());
        po.setStatus(settlement.getStatus());
        po.setCreateTime(settlement.getCreateTime());
        po.setUpdateTime(settlement.getUpdateTime());
        return po;
    }
}
