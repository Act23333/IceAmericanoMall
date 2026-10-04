package org.icedamericanomall.merchant.infrastructure.persistence.shop;

import org.icedamericanomall.merchant.domain.model.shop.ShopStatusEnum;
import org.icedamericanomall.merchant.domain.model.shop.Shop;

/**
 * {@code ShopPO} ↔ 领域对象 {@link Shop} 转换器（基础设施层内部，纯静态转换）。
 */
public final class ShopConverter {

    private ShopConverter() {
    }

    /** PO → 领域对象（输入为 null 时返回 null）。 */
    public static Shop toDomain(ShopPO dataObject) {
        if (dataObject == null) {
            return null;
        }
        return Shop.builder()
                .id(dataObject.getId())
                .merchantId(dataObject.getMerchantId())
                .shopName(dataObject.getShopName())
                .shopLogo(dataObject.getShopLogo())
                .contactPhone(dataObject.getContactPhone())
                .province(dataObject.getProvince())
                .city(dataObject.getCity())
                .district(dataObject.getDistrict())
                .detailAddress(dataObject.getDetailAddress())
                .status(ShopStatusEnum.fromCode(dataObject.getStatus()))
                .createTime(dataObject.getCreateTime())
                .updateTime(dataObject.getUpdateTime())
                .build();
    }

    /** 领域对象 → PO（输入为 null 时返回 null）。 */
    public static ShopPO toDataObject(Shop shop) {
        if (shop == null) {
            return null;
        }
        ShopPO dataObject = new ShopPO();
        dataObject.setId(shop.getId());
        dataObject.setMerchantId(shop.getMerchantId());
        dataObject.setShopName(shop.getShopName());
        dataObject.setShopLogo(shop.getShopLogo());
        dataObject.setContactPhone(shop.getContactPhone());
        dataObject.setProvince(shop.getProvince());
        dataObject.setCity(shop.getCity());
        dataObject.setDistrict(shop.getDistrict());
        dataObject.setDetailAddress(shop.getDetailAddress());
        dataObject.setStatus(shop.getStatus() == null ? null : shop.getStatus().getCode());
        dataObject.setCreateTime(shop.getCreateTime());
        dataObject.setUpdateTime(shop.getUpdateTime());
        return dataObject;
    }
}
