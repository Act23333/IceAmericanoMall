package org.icedamericanomall.user.adapter.response;

import lombok.Data;
import org.icedamericanomall.user.domain.model.Address;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 收货地址响应 VO（JSON 报文与重构前逐字段一致）。
 */
@Data
public class AddressResp {

    /** 地址唯一标识 */
    private Long id;

    /** 所属用户ID（技术主键口径） */
    private Long userId;

    private String receiver;

    private String phone;

    private String province;

    private String city;

    private String district;

    private String street;

    private String detail;

    /** 是否默认地址：1-是，0-否 */
    private Boolean defaulted;

    /** 地址标签（如"家"、"公司"） */
    private String label;

    /** 经度（可选） */
    private BigDecimal longitude;

    /** 纬度（可选） */
    private BigDecimal latitude;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    /** 领域模型 → 响应 VO。 */
    public static AddressResp from(Address address) {
        AddressResp resp = new AddressResp();
        resp.setId(address.getId());
        resp.setUserId(address.getUserId());
        resp.setReceiver(address.getReceiver());
        resp.setPhone(address.getPhone());
        resp.setProvince(address.getProvince());
        resp.setCity(address.getCity());
        resp.setDistrict(address.getDistrict());
        resp.setStreet(address.getStreet());
        resp.setDetail(address.getDetail());
        resp.setDefaulted(address.getDefaulted());
        resp.setLabel(address.getLabel());
        resp.setLongitude(address.getLongitude());
        resp.setLatitude(address.getLatitude());
        resp.setCreateTime(address.getCreateTime());
        resp.setUpdateTime(address.getUpdateTime());
        return resp;
    }
}
