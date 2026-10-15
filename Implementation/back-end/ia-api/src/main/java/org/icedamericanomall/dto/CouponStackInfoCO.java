package org.icedamericanomall.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * V4.3: 优惠券叠加信息（trade-service 下单时批量查询，用于叠加规则校验）。
 *
 * <p>查询口径：{@code GET /internal/coupon/batch?ids=} 的 {@code ids} 是
 * <b>{@code user_coupon.id}</b>（与 {@code POST /internal/coupon/use} 的 {@code userCouponId} 同口径），
 * 故 {@link #id} 即持券 ID，供调用方按原入参对应；{@link #couponId} 为券模板的业务券号。
 */
@Data
public class CouponStackInfoCO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;              // user_coupon.id（与 /use 的 userCouponId 同口径）
    private String couponId;      // 券模板业务ID（coupon.coupon_id hex）
    private Integer couponCategory; // PLATFORM/SHOP/FLASH_SALE/EXCLUSIVE
    private Integer stackRule;    // MUTUAL_EXCLUSIVE / STACKABLE
    private String stackGroup;    // 叠加分组
    private Integer discountType;
    private Long sellerId;
}
