package org.icedAmericanoMall.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.icedAmericanoMall.domain.entity.SellerEntity;
import org.icedAmericanoMall.mapper.SellerMapper;
import org.icedAmericanoMall.service.SellerService;
import org.noLazy.common.enums.ErrorCode;
import org.noLazy.common.exception.BizException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SellerServiceImpl extends ServiceImpl<SellerMapper, SellerEntity> implements SellerService {

    @Override
    public SellerEntity getByUserId(Long userId) {
        return lambdaQuery().eq(SellerEntity::getUserId, userId).one();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void register(SellerEntity seller) {
        SellerEntity existing = getByUserId(seller.getUserId());
        if (existing != null) {
            throw new BizException(ErrorCode.USER_ALREADY_EXISTS, "已申请过商家");
        }
        seller.setStatus(0); // 审核中
        save(seller);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, Integer status) {
        SellerEntity seller = getById(id);
        if (seller == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND, "商家不存在");
        }
        lambdaUpdate().eq(SellerEntity::getId, id).set(SellerEntity::getStatus, status).update();
    }
}
