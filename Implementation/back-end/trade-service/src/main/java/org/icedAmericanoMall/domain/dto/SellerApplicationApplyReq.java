package org.icedAmericanoMall.domain.dto;

import lombok.Data;

import java.io.Serializable;

/** 商家入驻申请请求。userId 由登录态注入。 */
@Data
public class SellerApplicationApplyReq implements Serializable {
    private static final long serialVersionUID = 1L;

    private String shopName;
    private String contactPhone;
    private String province;
    private String city;
    private String district;
    private String detailAddress;
    private String description;
}
