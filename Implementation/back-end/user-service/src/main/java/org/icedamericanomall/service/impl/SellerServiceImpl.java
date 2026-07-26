package org.icedamericanomall.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.icedamericanomall.convert.SellerConverter;
import org.icedamericanomall.domain.dto.SellerRegisterReq;
import org.icedamericanomall.domain.dto.UpdateShopReq;
import org.icedamericanomall.domain.entity.SellerEntity;
import org.icedamericanomall.domain.vo.SellerVO;
import org.icedamericanomall.mapper.SellerMapper;
import org.icedamericanomall.service.SellerService;
import org.noLazy.common.enums.ErrorCode;
import org.noLazy.common.exception.BizException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SellerServiceImpl extends ServiceImpl<SellerMapper, SellerEntity> implements SellerService {

    private static final int STATUS_PENDING = 0;

    private final SellerConverter sellerConverter;

    @Override
    public SellerEntity getByUserId(Long userId) {
        return lambdaQuery().eq(SellerEntity::getUserId, userId).one();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void register(Long userId, SellerRegisterReq req) {
        if (getByUserId(userId) != null) {
            throw new BizException(ErrorCode.USER_ALREADY_EXISTS, "已申请过商家");
        }
        SellerEntity seller = new SellerEntity();
        seller.setUserId(userId);
        seller.setShopName(req.getShopName());
        seller.setContactPhone(req.getContactPhone());
        seller.setProvince(req.getProvince());
        seller.setCity(req.getCity());
        seller.setDistrict(req.getDistrict());
        seller.setDetailAddress(req.getDetailAddress());
        seller.setStatus(STATUS_PENDING); // 审核中
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

    @Override
    public SellerVO getShopVO(Long userId) {
        SellerEntity seller = getByUserId(userId);
        if (seller == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND, "您尚未开通商家");
        }
        return sellerConverter.toVO(seller);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateShop(Long userId, UpdateShopReq req) {
        SellerEntity seller = getByUserId(userId);
        if (seller == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND, "您尚未开通商家");
        }
        var updater = lambdaUpdate().eq(SellerEntity::getId, seller.getId());
        if (req.getShopName() != null && !req.getShopName().isBlank()) {
            updater.set(SellerEntity::getShopName, req.getShopName());
        }
        if (req.getShopLogo() != null && !req.getShopLogo().isBlank()) {
            updater.set(SellerEntity::getShopLogo, req.getShopLogo());
        }
        if (req.getContactPhone() != null && !req.getContactPhone().isBlank()) {
            updater.set(SellerEntity::getContactPhone, req.getContactPhone());
        }
        if (req.getProvince() != null) updater.set(SellerEntity::getProvince, req.getProvince());
        if (req.getCity() != null) updater.set(SellerEntity::getCity, req.getCity());
        if (req.getDistrict() != null) updater.set(SellerEntity::getDistrict, req.getDistrict());
        if (req.getDetailAddress() != null) updater.set(SellerEntity::getDetailAddress, req.getDetailAddress());
        updater.update();
    }

    @Override
    public IPage<SellerVO> pagePending(int page, int size) {
        IPage<SellerEntity> result = lambdaQuery()
                .eq(SellerEntity::getStatus, STATUS_PENDING)
                .page(new Page<>(page, size));
        return result.convert(sellerConverter::toVO);
    }
}
