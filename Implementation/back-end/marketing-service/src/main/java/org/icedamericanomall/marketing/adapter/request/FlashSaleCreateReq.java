package org.icedamericanomall.marketing.adapter.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 秒杀活动创建请求（适配层入参）—— 平台后台 {@code POST /api/admin/flash}（V5.7 AD40）。
 *
 * <p>金额为整数分（flashPrice &gt; 0）；单字段合法性由 JSR-303 绑定期校验
 * （非法 → {@code PARAM_ERROR(1101)} / 400），跨字段不变式（endTime &gt; startTime）
 * 由聚合 {@code FlashSale.ensureTimeWindowOrdered} 守护。
 */
@Data
public class FlashSaleCreateReq implements Serializable {
    private static final long serialVersionUID = 1L;

    @NotNull
    private Long spuId;

    @NotNull
    private Long skuId;

    /** 秒杀价（分，&gt;0）。 */
    @NotNull
    @Positive
    private Integer flashPrice;

    /** 秒杀库存（&gt;0）。 */
    @NotNull
    @Positive
    private Integer stock;

    @NotNull
    private LocalDateTime startTime;

    @NotNull
    private LocalDateTime endTime;
}
