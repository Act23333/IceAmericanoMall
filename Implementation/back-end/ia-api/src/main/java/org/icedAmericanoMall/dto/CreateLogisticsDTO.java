package org.icedAmericanoMall.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 创建物流记录 DTO — trade-service → logistics-service
 */
@Data
public class CreateLogisticsDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long orderId;
    private String logisticsNumber;
    private String logisticsCompany;
    private String contact;
    private String mobile;
    private String province;
    private String city;
    private String district;
    private String street;
    private String detail;
}
