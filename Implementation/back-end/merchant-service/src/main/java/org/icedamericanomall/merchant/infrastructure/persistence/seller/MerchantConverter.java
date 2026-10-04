package org.icedamericanomall.merchant.infrastructure.persistence.seller;

import org.icedamericanomall.merchant.domain.enums.MerchantStatusEnum;
import org.icedamericanomall.merchant.domain.model.merchant.Merchant;

/**
 * {@code MerchantPO} ↔ 领域对象 {@link Merchant} 转换器（基础设施层内部，纯静态转换）。
 *
 * <p>V5.4（ADR-019）后只转换商家身份 + 生命周期列，店铺资料不在本表。
 */
public final class MerchantConverter {

    private MerchantConverter() {
    }

    /** PO → 领域对象（输入为 null 时返回 null）。 */
    public static Merchant toDomain(MerchantPO dataObject) {
        if (dataObject == null) {
            return null;
        }
        return Merchant.builder()
                .id(dataObject.getId())
                .userId(dataObject.getUserId())
                .status(MerchantStatusEnum.fromCode(dataObject.getStatus()))
                .createTime(dataObject.getCreateTime())
                .updateTime(dataObject.getUpdateTime())
                .build();
    }

    /** 领域对象 → PO（输入为 null 时返回 null）。 */
    public static MerchantPO toDataObject(Merchant merchant) {
        if (merchant == null) {
            return null;
        }
        MerchantPO dataObject = new MerchantPO();
        dataObject.setId(merchant.getId());
        dataObject.setUserId(merchant.getUserId());
        dataObject.setStatus(merchant.getStatus() == null ? null : merchant.getStatus().getCode());
        dataObject.setCreateTime(merchant.getCreateTime());
        dataObject.setUpdateTime(merchant.getUpdateTime());
        return dataObject;
    }
}
