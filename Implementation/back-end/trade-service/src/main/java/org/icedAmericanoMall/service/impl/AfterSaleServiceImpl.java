package org.icedAmericanoMall.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.icedAmericanoMall.domain.entity.AfterSaleEntity;
import org.icedAmericanoMall.mapper.AfterSaleMapper;
import org.icedAmericanoMall.service.AfterSaleService;
import org.springframework.stereotype.Service;

@Service
public class AfterSaleServiceImpl extends ServiceImpl<AfterSaleMapper, AfterSaleEntity> implements AfterSaleService {

    @Override
    public boolean existsByOrderAndUser(String orderNo, Long userId) {
        return lambdaQuery()
                .eq(AfterSaleEntity::getOrderNo, orderNo)
                .eq(AfterSaleEntity::getUserId, userId)
                .exists();
    }

    @Override
    public IPage<AfterSaleEntity> pageByUserId(Long userId, int page, int size) {
        return lambdaQuery()
                .eq(AfterSaleEntity::getUserId, userId)
                .orderByDesc(AfterSaleEntity::getCreateTime)
                .page(new Page<>(page, size));
    }

    @Override
    public IPage<AfterSaleEntity> pageByStatus(Integer status, int page, int size) {
        var wrapper = lambdaQuery().orderByDesc(AfterSaleEntity::getCreateTime);
        if (status != null) wrapper.eq(AfterSaleEntity::getStatus, status);
        return wrapper.page(new Page<>(page, size));
    }
}
