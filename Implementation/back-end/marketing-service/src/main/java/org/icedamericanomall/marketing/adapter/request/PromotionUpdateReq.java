package org.icedamericanomall.marketing.adapter.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 促销活动编辑请求（适配层入参）—— 平台后台 {@code PUT /api/admin/promotion/{id}}（V5.8 AD49）。
 *
 * <p>与 {@link PromotionCreateReq} 同字段但全可选：null 字段不更新（null-safe 局部更新，
 * 合并语义见 {@code MarketingConverter.mergePromotionUpdate}）；
 * {@code @NotBlank}/{@code @Positive} 对 null 放行，仅在传入非法值时拒绝
 * （非法 → {@code PARAM_ERROR(1101)} / 400）。「已结束活动不可编辑」守卫在应用层
 * （时间窗实时推导），合并后的满折越界/时间窗无序由聚合不变式守护。
 */
@Data
public class PromotionUpdateReq implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 活动名称（可选，传入时 ≤50 且非空）。 */
    @NotBlank
    @Size(max = 50)
    private String name;

    private Long sellerId;

    /** 活动类型（可选）：1=满减 2=满折；非法值由聚合 {@code ensureDiscountRuleLegal} 拒绝。 */
    private Integer type;

    /** 门槛（分，&gt;0，可选）。 */
    @Positive
    private Integer threshold;

    /** 满减=减免金额（分）；满折=万分比（可选，≤10000 由聚合守护）。 */
    @Positive
    private Integer discountValue;

    /** 满折封顶减免额（分，可选）；显式 0=取消封顶。 */
    @PositiveOrZero
    private Integer maxDiscount;

    private LocalDateTime startTime;

    private LocalDateTime endTime;
}
