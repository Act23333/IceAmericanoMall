package org.icedamericanomall.domain.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * V4.3: 结算页优惠券可用性 VO（京东标准：每张券带是否可用+不可用原因）。
 */
@Data
public class CouponAvailableVO implements Serializable {
    private static final long serialVersionUID = 1L;

    /** user_coupon 记录ID（下单时传入） */
    private Long userCouponId;
    /** 优惠券模板ID */
    private String couponId;
    /** 券名 */
    private String name;
    /** 折扣类型: 1=FIXED, 2=PERCENTAGE, 3=CASH_COUPON */
    private Integer discountType;
    /** 面值(分) */
    private Integer value;
    /** 最低消费(分) */
    private Integer minAmount;
    /** 券类别 */
    private Integer couponCategory;
    /** 适用范围 */
    private Integer scopeType;
    /** 过期时间 */
    private LocalDateTime endTime;
    /** 是否适用于当前订单 */
    private Boolean applicable;
    /** 不适用原因（applicable=false时有值） */
    private String unapplicableReason;
    /** 预估折扣金额（applicable=true时有值，仅作参考） */
    private Integer estimatedDiscount;
}
