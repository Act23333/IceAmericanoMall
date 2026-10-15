package org.icedamericanomall.marketing.adapter.response;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户优惠券视图对象（适配层出参）。
 *
 * <p>字段口径（契约 §4.10.1，05-API-Specification 为唯一真相源）：
 * {@code id}/{@code coupon_id}/{@code status}/{@code used_order_no}/{@code use_time}/
 * {@code create_time} 为持券聚合本体字段；{@code name}/{@code value}/{@code min_amount}
 * 为<b>券面展示字段</b>，由 {@code UserCouponViewAssembler} 按券模板 ID 批量回填
 * （ADR-057：批量 + fail-open；查不到模板 → 三字段为 null，券列表照常返回）。
 */
@Data
public class UserCouponVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long couponId;
    private Integer status;
    private String usedOrderNo;
    private LocalDateTime useTime;
    private LocalDateTime createTime;

    // ==================== 券面展示字段（ADR-057 回填） ====================

    /** 券名称（{@code coupon.name}）；模板已删除时为 null */
    private String name;

    /** 券面值（分；满减=减免金额，折扣=百分比口径） */
    private Integer value;

    /** 最低消费金额（分） */
    private Integer minAmount;
}
