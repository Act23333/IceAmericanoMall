package org.icedamericanomall.user.infrastructure.persistence.seller;

import org.icedamericanomall.user.domain.model.Seller;
import org.icedamericanomall.user.domain.model.SellerStatusEnum;

/**
 * {@code SellerDO} ↔ 领域对象 {@link Seller} 转换器（基础设施层内部，纯静态转换）。
 */
public final class SellerConverter {

    private SellerConverter() {
    }

    /** DO → 领域对象（输入为 null 时返回 null）。 */
    public static Seller toDomain(SellerDO dataObject) {
        if (dataObject == null) {
            return null;
        }
        return Seller.builder()
                .id(dataObject.getId())
                .userId(dataObject.getUserId())
                .shopName(dataObject.getShopName())
                .shopLogo(dataObject.getShopLogo())
                .contactPhone(dataObject.getContactPhone())
                .province(dataObject.getProvince())
                .city(dataObject.getCity())
                .district(dataObject.getDistrict())
                .detailAddress(dataObject.getDetailAddress())
                .status(SellerStatusEnum.fromCode(dataObject.getStatus()))
                .createTime(dataObject.getCreateTime())
                .updateTime(dataObject.getUpdateTime())
                .build();
    }

    /** 领域对象 → DO（输入为 null 时返回 null）。 */
    public static SellerDO toDataObject(Seller seller) {
        if (seller == null) {
            return null;
        }
        SellerDO dataObject = new SellerDO();
        dataObject.setId(seller.getId());
        dataObject.setUserId(seller.getUserId());
        dataObject.setShopName(seller.getShopName());
        dataObject.setShopLogo(seller.getShopLogo());
        dataObject.setContactPhone(seller.getContactPhone());
        dataObject.setProvince(seller.getProvince());
        dataObject.setCity(seller.getCity());
        dataObject.setDistrict(seller.getDistrict());
        dataObject.setDetailAddress(seller.getDetailAddress());
        dataObject.setStatus(seller.getStatus() == null ? null : seller.getStatus().getCode());
        dataObject.setCreateTime(seller.getCreateTime());
        dataObject.setUpdateTime(seller.getUpdateTime());
        return dataObject;
    }
}
