package org.icedamericanomall.marketing.adapter.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 拼团活动编辑请求（适配层入参）—— 平台后台 {@code PUT /api/admin/group-buy/{id}}（V5.14 AD55）。
 *
 * <p>与 {@link GroupBuyCreateReq} 同字段但全可选：null 字段不更新（null-safe 局部更新，
 * 合并语义见 {@code MarketingConverter.mergeGroupBuyUpdate}）；
 * {@code @Size}/{@code @Positive}/{@code @Min} 对 null 放行，仅在传入非法值时拒绝
 * （非法 → {@code PARAM_ERROR(1101)} / 400）。「已结束活动不可编辑」守卫在应用层
 * （时间窗实时推导），合并后的字段越界/时间窗无序由聚合不变式守护。
 */
@Data
public class GroupBuyUpdateReq implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 活动名称（可选；不传/空白 = 不更新，传入时 ≤50）。 */
    @Size(max = 50)
    private String name;

    private Long sellerId;

    private Long productId;

    private Long skuId;

    /** 拼团价（分，&gt;0，可选）。 */
    @Positive
    private Integer groupPrice;

    /** 成团人数（≥2，可选）。 */
    @Min(2)
    private Integer requiredMembers;

    /** 成团时限（小时，≥1，可选）。 */
    @Min(1)
    private Integer timeLimitHours;

    private LocalDateTime startTime;

    private LocalDateTime endTime;
}
