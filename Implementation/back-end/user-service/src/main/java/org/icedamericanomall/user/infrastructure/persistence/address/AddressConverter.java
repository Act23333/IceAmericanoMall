package org.icedamericanomall.user.infrastructure.persistence.address;

import org.icedamericanomall.user.domain.model.address.Address;

/**
 * {@code AddressPO} ↔ 领域对象 {@link Address} 转换器（基础设施层内部，纯静态转换）。
 */
public final class AddressConverter {

    private AddressConverter() {
    }

    /** PO → 领域对象（输入为 null 时返回 null）。 */
    public static Address toDomain(AddressPO dataObject) {
        if (dataObject == null) {
            return null;
        }
        return Address.builder()
                .id(dataObject.getId())
                .userId(dataObject.getUserId())
                .receiver(dataObject.getReceiver())
                .phone(dataObject.getPhone())
                .province(dataObject.getProvince())
                .city(dataObject.getCity())
                .district(dataObject.getDistrict())
                .street(dataObject.getStreet())
                .detail(dataObject.getDetail())
                .defaulted(dataObject.getDefaulted())
                .label(dataObject.getLabel())
                .longitude(dataObject.getLongitude())
                .latitude(dataObject.getLatitude())
                .createTime(dataObject.getCreateTime())
                .updateTime(dataObject.getUpdateTime())
                .build();
    }

    /** 领域对象 → PO（输入为 null 时返回 null）。 */
    public static AddressPO toDataObject(Address address) {
        if (address == null) {
            return null;
        }
        AddressPO dataObject = new AddressPO();
        dataObject.setId(address.getId());
        dataObject.setUserId(address.getUserId());
        dataObject.setReceiver(address.getReceiver());
        dataObject.setPhone(address.getPhone());
        dataObject.setProvince(address.getProvince());
        dataObject.setCity(address.getCity());
        dataObject.setDistrict(address.getDistrict());
        dataObject.setStreet(address.getStreet());
        dataObject.setDetail(address.getDetail());
        dataObject.setDefaulted(address.getDefaulted());
        dataObject.setLabel(address.getLabel());
        dataObject.setLongitude(address.getLongitude());
        dataObject.setLatitude(address.getLatitude());
        dataObject.setCreateTime(address.getCreateTime());
        dataObject.setUpdateTime(address.getUpdateTime());
        return dataObject;
    }
}
