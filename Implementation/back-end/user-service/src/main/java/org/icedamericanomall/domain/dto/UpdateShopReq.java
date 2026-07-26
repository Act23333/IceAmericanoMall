package org.icedamericanomall.domain.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 店铺设置更新请求
 */
@Data
public class UpdateShopReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String shopName;
    private String shopLogo;
    private String contactPhone;
    private String province;
    private String city;
    private String district;
    private String detailAddress;
}
