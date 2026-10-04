package org.icedamericanomall.trade.infrastructure.persistence.aftersale;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.icedamericanomall.trade.domain.model.aftersale.AfterSale;
import org.icedamericanomall.trade.domain.repository.AfterSaleRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 售后单仓储实现 —— PO 与聚合互转在本类完成。
 *
 * <p>条件更新用显式 SQL（{@link AfterSaleMapper#updateStatusConditionally}）而非
 * {@code lambdaUpdate()} 链：把「WHERE 带期望状态」这一不变量落到 Mapper 契约上。
 */
@Repository
@RequiredArgsConstructor
public class AfterSaleRepositoryImpl implements AfterSaleRepository {

    private final AfterSaleMapper afterSaleMapper;

    @Override
    public AfterSale findById(Long id) {
        return toDomain(afterSaleMapper.selectById(id));
    }

    @Override
    public AfterSale save(AfterSale afterSale) {
        AfterSalePO po = toPO(afterSale);
        afterSaleMapper.insert(po);
        afterSale.setId(po.getId());
        return afterSale;
    }

    @Override
    public void update(AfterSale afterSale) {
        afterSaleMapper.updateById(toPO(afterSale));
    }

    @Override
    public boolean existsByOrderNoAndUserId(String orderNo, Long userId) {
        return afterSaleMapper.selectCount(new LambdaQueryWrapper<AfterSalePO>()
                .eq(AfterSalePO::getOrderNo, orderNo)
                .eq(AfterSalePO::getUserId, userId)) > 0;
    }

    @Override
    public IPage<AfterSale> pageByUserId(Long userId, int page, int size) {
        return afterSaleMapper.selectPage(new Page<>(page, size),
                        new LambdaQueryWrapper<AfterSalePO>()
                                .eq(AfterSalePO::getUserId, userId)
                                .orderByDesc(AfterSalePO::getCreateTime))
                .convert(AfterSaleRepositoryImpl::toDomain);
    }

    @Override
    public IPage<AfterSale> pageByStatus(Integer status, int page, int size) {
        var wrapper = new LambdaQueryWrapper<AfterSalePO>().orderByDesc(AfterSalePO::getCreateTime);
        if (status != null) {
            wrapper.eq(AfterSalePO::getStatus, status);
        }
        return afterSaleMapper.selectPage(new Page<>(page, size), wrapper)
                .convert(AfterSaleRepositoryImpl::toDomain);
    }

    @Override
    public int updateStatusConditionally(Long id, int expectedStatus, int targetStatus) {
        return afterSaleMapper.updateStatusConditionally(id, expectedStatus, targetStatus);
    }

    @Override
    public int updateStatusConditionallyFrom(Long id, List<Integer> expectedStatuses, int targetStatus) {
        return afterSaleMapper.updateStatusConditionallyFrom(id, expectedStatuses, targetStatus);
    }

    // ==================== PO <-> 聚合 ====================

    static AfterSale toDomain(AfterSalePO po) {
        if (po == null) {
            return null;
        }
        AfterSale afterSale = new AfterSale();
        afterSale.setId(po.getId());
        afterSale.setOrderNo(po.getOrderNo());
        afterSale.setUserId(po.getUserId());
        afterSale.setType(po.getType());
        afterSale.setReason(po.getReason());
        afterSale.setStatus(po.getStatus());
        afterSale.setAdminRemark(po.getAdminRemark());
        afterSale.setRefundAmount(po.getRefundAmount());
        afterSale.setLogisticsNumber(po.getLogisticsNumber());
        afterSale.setCreateTime(po.getCreateTime());
        afterSale.setUpdateTime(po.getUpdateTime());
        return afterSale;
    }

    static AfterSalePO toPO(AfterSale afterSale) {
        AfterSalePO po = new AfterSalePO();
        po.setId(afterSale.getId());
        po.setOrderNo(afterSale.getOrderNo());
        po.setUserId(afterSale.getUserId());
        po.setType(afterSale.getType());
        po.setReason(afterSale.getReason());
        po.setStatus(afterSale.getStatus());
        po.setAdminRemark(afterSale.getAdminRemark());
        po.setRefundAmount(afterSale.getRefundAmount());
        po.setLogisticsNumber(afterSale.getLogisticsNumber());
        po.setCreateTime(afterSale.getCreateTime());
        po.setUpdateTime(afterSale.getUpdateTime());
        return po;
    }
}
