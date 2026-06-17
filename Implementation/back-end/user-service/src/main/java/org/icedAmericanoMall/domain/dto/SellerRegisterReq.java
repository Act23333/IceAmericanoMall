package org.icedAmericanoMall.domain.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 商家注册请求 — 用户申请成为商家
 */
@Data
public class SellerRegisterReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "店铺名称不能为空")
    private String shopName;

    @NotBlank(message = "联系电话不能为空")
    private String contactPhone;

    private String province;
    private String city;
    private String district;
    private String detailAddress;
}
