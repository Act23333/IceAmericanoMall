package org.icedAmericanoMall.domain.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 商家入驻申请视图对象。 */
@Data
public class SellerApplicationVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long userId;
    private String shopName;
    private String contactPhone;
    private String province;
    private String city;
    private String district;
    private String detailAddress;
    private String description;
    private Integer status;
    private String adminRemark;
    private LocalDateTime createTime;
}
