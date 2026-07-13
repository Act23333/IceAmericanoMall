package org.icedAmericanoMall.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.icedAmericanoMall.domain.dto.AfterSaleApplyReq;
import org.icedAmericanoMall.domain.entity.AfterSaleEntity;
import org.icedAmericanoMall.mapper.AfterSaleMapper;
import org.icedAmericanoMall.service.AfterSaleService;
import org.noLazy.common.enums.ErrorCode;
import org.noLazy.common.exception.BizException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AfterSaleServiceImpl extends ServiceImpl<AfterSaleMapper, AfterSaleEntity> implements AfterSaleService {

    private static final int STATUS_PENDING = 1;

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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AfterSaleEntity applyAfterSale(Long userId, AfterSaleApplyReq req) {
        if (existsByOrderAndUser(req.getOrderNo(), userId)) {
            throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "已有售后申请");
        }
        AfterSaleEntity entity = new AfterSaleEntity();
        entity.setUserId(userId);
        entity.setOrderNo(req.getOrderNo());
        entity.setType(req.getType());
        entity.setReason(req.getReason());
        entity.setRefundAmount(req.getRefundAmount());
        entity.setLogisticsNumber(req.getLogisticsNumber());
        entity.setStatus(STATUS_PENDING);
        save(entity);
        return entity;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void review(Long id, Integer status, String adminRemark) {
        AfterSaleEntity entity = getById(id);
        if (entity == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND, "售后申请不存在");
        }
        entity.setStatus(status);
        entity.setAdminRemark(adminRemark);
        updateById(entity);
    }
}
