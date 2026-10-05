package org.icedamericanomall.marketing.adapter.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 促销活动创建请求（适配层入参）—— 平台后台 {@code POST /api/admin/promotion}（V5.8 AD48）。
 *
 * <p>店铺级满减/满折活动：金额为整数分（threshold/discountValue &gt; 0），满折折扣率为万分比
 * （8500=8.5折）。单字段合法性由 JSR-303 绑定期校验（非法 → {@code PARAM_ERROR(1101)} / 400），
 * 跨字段不变式由聚合守护：endTime &gt; startTime 见 {@code Promotion.ensureTimeWindowOrdered}；
 * 满折 discountValue ≤ 10000 见 {@code Promotion.ensureDiscountRuleLegal}。
 */
@Data
public class PromotionCreateReq implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 活动名称（必填，≤50）。 */
    @NotBlank
    @Size(max = 50)
    private String name;

    /** 商家 user_id（店铺级活动，Phase 1 必填）。 */
    @NotNull
    private Long sellerId;

    /** 活动类型：1=满减(FIXED) 2=满折(PERCENTAGE)。 */
    @NotNull
    private Integer type;

    /** 门槛：订单商品总额 ≥ threshold 生效（分，&gt;0）。 */
    @NotNull
    @Positive
    private Integer threshold;

    /** 满减=减免金额（分）；满折=折扣率万分比（8500=8.5折，≤10000）。 */
    @NotNull
    @Positive
    private Integer discountValue;

    /** 满折封顶减免额（分）；缺省或显式 0=不封顶（仅满折语义使用）。 */
    @PositiveOrZero
    private Integer maxDiscount;

    @NotNull
    private LocalDateTime startTime;

    @NotNull
    private LocalDateTime endTime;
}
