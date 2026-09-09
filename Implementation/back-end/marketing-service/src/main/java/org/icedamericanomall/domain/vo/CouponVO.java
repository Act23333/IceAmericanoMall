package org.icedamericanomall.domain.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class CouponVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String couponId;
    private String name;
    private Integer type;
    private Integer value;
    private Integer minAmount;
    private Long sellerId;
    private Integer totalQty;
    private Integer issuedQty;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer status;

    // V4.0: 多维度优惠券模型
    private Integer discountType;
    private Integer couponCategory;
    private Integer grantType;
    private Integer stockType;
    private Integer grabType;
    private Integer priceInCents;

    // V4.3: 适用范围 + 叠加规则
    private Integer scopeType;
    private String scopeValues;
    private Integer stackRule;
    private String stackGroup;
}
