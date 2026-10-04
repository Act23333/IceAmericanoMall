package org.icedamericanomall.merchant.infrastructure.persistence.shop;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.merchant.domain.model.shop.Shop;
import org.icedamericanomall.merchant.domain.repository.ShopRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 店铺仓储实现（基础设施层）。
 *
 * <p>业务唯一键 {@code (merchant_id, shop_name)}（uk_merchant_shop_name）：
 * 重复店名在持久化层翻译为业务异常，不向调用方暴露框架异常。
 * 资料更新带 {@code id + merchant_id} 双条件，越权改写返回 0 行。
 */
@Repository
@RequiredArgsConstructor
public class ShopRepositoryImpl implements ShopRepository {

    private final ShopMapper shopMapper;

    @Override
    public Shop insert(Shop shop) {
        ShopPO dataObject = ShopConverter.toDataObject(shop);
        dataObject.setId(null);
        try {
            shopMapper.insert(dataObject);
        } catch (DuplicateKeyException e) {
            throw new BizException(ErrorCode.PARAM_ERROR, "店铺名称已存在");
        }
        return ShopConverter.toDomain(dataObject);
    }

    @Override
    public Optional<Shop> findById(Long shopId) {
        if (shopId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(ShopConverter.toDomain(shopMapper.selectById(shopId)));
    }

    @Override
    public Optional<Shop> findDefaultByMerchantId(Long merchantId) {
        if (merchantId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(ShopConverter.toDomain(shopMapper.selectOne(
                new LambdaQueryWrapper<ShopPO>()
                        .eq(ShopPO::getMerchantId, merchantId)
                        .orderByAsc(ShopPO::getId)
                        .last("LIMIT 1"))));
    }

    @Override
    public List<Shop> findByMerchantId(Long merchantId) {
        if (merchantId == null) {
            return List.of();
        }
        return shopMapper.selectList(new LambdaQueryWrapper<ShopPO>()
                        .eq(ShopPO::getMerchantId, merchantId)
                        .orderByAsc(ShopPO::getId))
                .stream()
                .map(ShopConverter::toDomain)
                .toList();
    }

    @Override
    public int updateProfile(Shop shop) {
        LambdaUpdateWrapper<ShopPO> update = new LambdaUpdateWrapper<ShopPO>()
                .eq(ShopPO::getId, shop.getId())
                .eq(ShopPO::getMerchantId, shop.getMerchantId())
                .set(ShopPO::getUpdateTime, shop.getUpdateTime());
        if (shop.getShopName() != null) update.set(ShopPO::getShopName, shop.getShopName());
        if (shop.getShopLogo() != null) update.set(ShopPO::getShopLogo, shop.getShopLogo());
        if (shop.getContactPhone() != null) update.set(ShopPO::getContactPhone, shop.getContactPhone());
        if (shop.getProvince() != null) update.set(ShopPO::getProvince, shop.getProvince());
        if (shop.getCity() != null) update.set(ShopPO::getCity, shop.getCity());
        if (shop.getDistrict() != null) update.set(ShopPO::getDistrict, shop.getDistrict());
        if (shop.getDetailAddress() != null) update.set(ShopPO::getDetailAddress, shop.getDetailAddress());
        return shopMapper.update(null, update);
    }

    @Override
    public int updateStatus(Shop shop) {
        return shopMapper.update(null, new LambdaUpdateWrapper<ShopPO>()
                .eq(ShopPO::getId, shop.getId())
                .set(ShopPO::getStatus, shop.getStatus() == null ? null : shop.getStatus().getCode())
                .set(ShopPO::getUpdateTime, shop.getUpdateTime()));
    }
}
