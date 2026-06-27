package org.icedAmericanoMall.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.icedAmericanoMall.domain.entity.SellerApplicationEntity;
import org.icedAmericanoMall.mapper.SellerApplicationMapper;
import org.icedAmericanoMall.service.ApplicationService;
import org.springframework.stereotype.Service;

@Service
public class ApplicationServiceImpl extends ServiceImpl<SellerApplicationMapper, SellerApplicationEntity>
        implements ApplicationService {

    @Override
    public boolean hasPendingApplication(Long userId) {
        return lambdaQuery()
                .eq(SellerApplicationEntity::getUserId, userId)
                .eq(SellerApplicationEntity::getStatus, 0)
                .exists();
    }

    @Override
    public SellerApplicationEntity getLatestApplication(Long userId) {
        return lambdaQuery()
                .eq(SellerApplicationEntity::getUserId, userId)
                .orderByDesc(SellerApplicationEntity::getCreateTime)
                .last("LIMIT 1")
                .one();
    }
}
