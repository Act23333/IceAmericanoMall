package org.icedamericanomall.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.icedamericanomall.domain.dto.SellerApplicationApplyReq;
import org.icedamericanomall.domain.entity.SellerApplicationEntity;
import org.icedamericanomall.mapper.SellerApplicationMapper;
import org.icedamericanomall.service.ApplicationService;
import org.noLazy.common.enums.ErrorCode;
import org.noLazy.common.exception.BizException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ApplicationServiceImpl extends ServiceImpl<SellerApplicationMapper, SellerApplicationEntity>
        implements ApplicationService {

    private static final int STATUS_PENDING = 0;

    @Override
    public boolean hasPendingApplication(Long userId) {
        return lambdaQuery()
                .eq(SellerApplicationEntity::getUserId, userId)
                .eq(SellerApplicationEntity::getStatus, STATUS_PENDING)
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SellerApplicationEntity applyForSeller(Long userId, SellerApplicationApplyReq req) {
        if (hasPendingApplication(userId)) {
            throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "已有审核中的申请");
        }
        SellerApplicationEntity entity = new SellerApplicationEntity();
        entity.setUserId(userId);
        entity.setShopName(req.getShopName());
        entity.setContactPhone(req.getContactPhone());
        entity.setProvince(req.getProvince());
        entity.setCity(req.getCity());
        entity.setDistrict(req.getDistrict());
        entity.setDetailAddress(req.getDetailAddress());
        entity.setDescription(req.getDescription());
        entity.setStatus(STATUS_PENDING);
        save(entity);
        return entity;
    }

    @Override
    public List<SellerApplicationEntity> listByStatus(Integer status) {
        var wrapper = lambdaQuery().orderByDesc(SellerApplicationEntity::getCreateTime);
        if (status != null) wrapper.eq(SellerApplicationEntity::getStatus, status);
        return wrapper.list();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void review(Long id, Integer status, String adminRemark) {
        SellerApplicationEntity app = getById(id);
        if (app == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND, "申请不存在");
        }
        app.setStatus(status);
        app.setAdminRemark(adminRemark);
        updateById(app);
    }
}
