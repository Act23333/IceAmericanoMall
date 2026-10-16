package org.icedamericanomall.marketing.adapter.request;

import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 秒杀活动编辑请求（适配层入参）—— 平台后台 {@code PUT /api/admin/flash/{id}}（V5.7 AD41）。
 *
 * <p>与 {@link FlashSaleCreateReq} 同字段但全可选：null 字段不更新（null-safe 局部更新，
 * 合并语义见 {@code MarketingConverter.mergeFlashSaleUpdate}）；
 * {@code @Positive} 对 null 放行，仅在传入 0/负值时拒绝（非法 → {@code PARAM_ERROR(1101)} / 400）。
 * 「已结束活动不可编辑」守卫在应用层（时间窗实时推导）。
 */
@Data
public class FlashSaleUpdateReq implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long spuId;

    private Long skuId;

    /** 秒杀价（分，&gt;0，可选）。 */
    @Positive
    private Integer flashPrice;

    /** 秒杀库存（&gt;0，可选）。 */
    @Positive
    private Integer stock;

    private LocalDateTime startTime;

    private LocalDateTime endTime;
}
