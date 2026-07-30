package org.icedamericanomall.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * V4.3: 优惠券叠加信息 DTO（trade-service 下单时批量查询，用于叠加规则校验）。
 */
@Data
public class CouponStackInfoDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;              // coupon.id
    private String couponId;      // 业务ID
    private Integer couponCategory; // PLATFORM/SHOP/FLASH_SALE/EXCLUSIVE
    private Integer stackRule;    // MUTUAL_EXCLUSIVE / STACKABLE
    private String stackGroup;    // 叠加分组
    private Integer discountType;
    private Long sellerId;
}
