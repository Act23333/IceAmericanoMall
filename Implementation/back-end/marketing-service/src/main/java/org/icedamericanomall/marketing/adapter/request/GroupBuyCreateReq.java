package org.icedamericanomall.marketing.adapter.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 拼团活动创建请求（适配层入参）—— 平台后台 {@code POST /api/admin/group-buy}（V5.14 AD54）。
 *
 * <p>金额为整数分（groupPrice &gt; 0），成团人数 ≥2，成团时限 ≥1 小时。
 * 单字段合法性由 JSR-303 绑定期校验（非法 → {@code PARAM_ERROR(1101)} / 400），
 * 跨字段不变式由聚合守护：endTime &gt; startTime 见 {@code GroupBuyActivity.ensureTimeWindowOrdered}，
 * 字段边界防线见 {@code GroupBuyActivity.ensureFieldsLegal}；时间窗整体已过期由应用层拒绝创建。
 */
@Data
public class GroupBuyCreateReq implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 活动名称（必填，≤50）。 */
    @NotBlank
    @Size(max = 50)
    private String name;

    /** 商家 user_id（店铺级活动）。 */
    @NotNull
    private Long sellerId;

    /** SPU 业务键（32-hex；契约 §1.8② 入参按业务键消费，服务端解析为技术键）。 */
    @NotBlank
    private String spuId;

    /** SKU 业务键（32-hex；拼团仅该 SKU 参与）。 */
    @NotBlank
    private String skuId;

    /** 拼团价（分，&gt;0；快照进拼团单 subTotal）。 */
    @NotNull
    @Positive
    private Integer groupPrice;

    /** 成团人数（≥2）。 */
    @NotNull
    @Min(2)
    private Integer requiredMembers;

    /** 成团时限（小时，≥1）。 */
    @NotNull
    @Min(1)
    private Integer timeLimitHours;

    @NotNull
    private LocalDateTime startTime;

    @NotNull
    private LocalDateTime endTime;
}
