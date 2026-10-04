package org.icedamericanomall.merchant.adapter.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 商家入驻申请请求 —— 用户申请成为商家（同时创建默认店铺，ADR-019）。
 *
 * <p>店铺资料列（省 / 市 / 区 / 详细地址）在 {@code shop} 表为 NOT NULL，
 * 故与店名、联系电话一样在此做非空校验，避免脏请求落到持久化层。
 */
@Data
public class MerchantApplyReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "店铺名称不能为空")
    private String shopName;

    @NotBlank(message = "联系电话不能为空")
    private String contactPhone;

    @NotBlank(message = "发货省不能为空")
    private String province;

    @NotBlank(message = "发货市不能为空")
    private String city;

    @NotBlank(message = "发货区/县不能为空")
    private String district;

    @NotBlank(message = "详细地址不能为空")
    private String detailAddress;
}
