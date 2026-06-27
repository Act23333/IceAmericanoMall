package org.icedAmericanoMall.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.icedAmericanoMall.domain.entity.WithdrawalEntity;
import org.icedAmericanoMall.mapper.WithdrawalMapper;
import org.icedAmericanoMall.service.WithdrawalService;
import org.springframework.stereotype.Service;

@Service
public class WithdrawalServiceImpl extends ServiceImpl<WithdrawalMapper, WithdrawalEntity> implements WithdrawalService {

    @Override
    public IPage<WithdrawalEntity> pageBySeller(Long sellerId, int page, int size) {
        return lambdaQuery()
                .eq(WithdrawalEntity::getSellerId, sellerId)
                .orderByDesc(WithdrawalEntity::getCreateTime)
                .page(new Page<>(page, size));
    }

    @Override
    public IPage<WithdrawalEntity> pageByStatus(Integer status, int page, int size) {
        var wrapper = lambdaQuery().orderByDesc(WithdrawalEntity::getCreateTime);
        if (status != null) wrapper.eq(WithdrawalEntity::getStatus, status);
        return wrapper.page(new Page<>(page, size));
    }
}
