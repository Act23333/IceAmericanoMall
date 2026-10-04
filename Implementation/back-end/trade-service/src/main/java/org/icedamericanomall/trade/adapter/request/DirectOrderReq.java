package org.icedamericanomall.trade.adapter.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * V4.0: 立即购买请求 — 京东标准（商品详情页直接下单，不操作购物车）。
 *
 * <p><b>契约收缩（V5.4）</b>：移除从未被 Controller 装配的 {@code userCouponIds}
 * （多券叠加属 roadmap，需 PRD；策略层能力保留），仅保留单券 {@code userCouponId}。
 */
@Data
public class DirectOrderReq implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotNull(message = "SKU ID不能为空")
    private Long skuId;

    @Min(value = 1, message = "数量至少为1")
    private Integer quantity = 1;

    @NotNull(message = "地址ID不能为空")
    private Long addressId;

    /** 优惠券 ID（单券模式，可选；多券叠加待 PRD）。 */
    private Long userCouponId;
}
