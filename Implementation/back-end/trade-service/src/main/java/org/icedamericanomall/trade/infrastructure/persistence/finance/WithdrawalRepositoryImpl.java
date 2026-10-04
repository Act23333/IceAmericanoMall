package org.icedamericanomall.trade.infrastructure.persistence.finance;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.icedamericanomall.trade.domain.model.finance.Withdrawal;
import org.icedamericanomall.trade.domain.repository.WithdrawalRepository;
import org.springframework.stereotype.Repository;

/**
 * 商家提现单仓储实现 —— PO 与聚合互转在本类完成。
 */
@Repository
@RequiredArgsConstructor
public class WithdrawalRepositoryImpl implements WithdrawalRepository {

    private final WithdrawalMapper withdrawalMapper;

    @Override
    public Withdrawal findById(Long id) {
        return toDomain(withdrawalMapper.selectById(id));
    }

    @Override
    public Withdrawal save(Withdrawal withdrawal) {
        WithdrawalPO po = toPO(withdrawal);
        withdrawalMapper.insert(po);
        withdrawal.setId(po.getId());
        return withdrawal;
    }

    @Override
    public void update(Withdrawal withdrawal) {
        withdrawalMapper.updateById(toPO(withdrawal));
    }

    @Override
    public IPage<Withdrawal> pageBySeller(Long sellerId, int page, int size) {
        return withdrawalMapper.selectPage(new Page<>(page, size),
                        new LambdaQueryWrapper<WithdrawalPO>()
                                .eq(WithdrawalPO::getSellerId, sellerId)
                                .orderByDesc(WithdrawalPO::getCreateTime))
                .convert(WithdrawalRepositoryImpl::toDomain);
    }

    @Override
    public IPage<Withdrawal> pageByStatus(Integer status, int page, int size) {
        var wrapper = new LambdaQueryWrapper<WithdrawalPO>().orderByDesc(WithdrawalPO::getCreateTime);
        if (status != null) {
            wrapper.eq(WithdrawalPO::getStatus, status);
        }
        return withdrawalMapper.selectPage(new Page<>(page, size), wrapper)
                .convert(WithdrawalRepositoryImpl::toDomain);
    }

    // ==================== PO <-> 聚合 ====================

    static Withdrawal toDomain(WithdrawalPO po) {
        if (po == null) {
            return null;
        }
        Withdrawal withdrawal = new Withdrawal();
        withdrawal.setId(po.getId());
        withdrawal.setWithdrawalNo(po.getWithdrawalNo());
        withdrawal.setSellerId(po.getSellerId());
        withdrawal.setAmount(po.getAmount());
        withdrawal.setStatus(po.getStatus());
        withdrawal.setBankAccount(po.getBankAccount());
        withdrawal.setBankName(po.getBankName());
        withdrawal.setAdminRemark(po.getAdminRemark());
        withdrawal.setCreateTime(po.getCreateTime());
        withdrawal.setUpdateTime(po.getUpdateTime());
        return withdrawal;
    }

    static WithdrawalPO toPO(Withdrawal withdrawal) {
        WithdrawalPO po = new WithdrawalPO();
        po.setId(withdrawal.getId());
        po.setWithdrawalNo(withdrawal.getWithdrawalNo());
        po.setSellerId(withdrawal.getSellerId());
        po.setAmount(withdrawal.getAmount());
        po.setStatus(withdrawal.getStatus());
        po.setBankAccount(withdrawal.getBankAccount());
        po.setBankName(withdrawal.getBankName());
        po.setAdminRemark(withdrawal.getAdminRemark());
        po.setCreateTime(withdrawal.getCreateTime());
        po.setUpdateTime(withdrawal.getUpdateTime());
        return po;
    }
}
