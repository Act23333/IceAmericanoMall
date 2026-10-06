package org.icedamericanomall.trade.adapter.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.icedamericanomall.trade.domain.model.order.Order;

import java.io.Serializable;

/**
 * V4.0: 立即购买请求 — 京东标准（商品详情页直接下单，不操作购物车）。
 *
 * <p><b>契约收缩（V5.4）</b>：移除从未被 Controller 装配的 {@code userCouponIds}
 * （多券叠加属 roadmap，需 PRD；策略层能力保留），仅保留单券 {@code userCouponId}。
 *
 * <p><b>V5.7</b>：增量 {@code remark} 买家留言（可选，≤200）—— 与购物车下单 T1 同构，
 * 随单快照落 {@code orders.remark} 并经 {@code OrderVO} 回显。
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

    /**
     * 买家留言（可选，≤ {@link Order#REMARK_MAX_LENGTH}）—— 下单时随单快照落库。
     * 可选字段不加 {@code @NotBlank}；超长由 {@code @Size} 前置拦截（→ 400 PARAM_ERROR）。
     */
    @Size(max = Order.REMARK_MAX_LENGTH, message = "买家留言不能超过" + Order.REMARK_MAX_LENGTH + "字")
    private String remark;

    /**
     * 展示币种（V5.17 多币种，可选，ISO 4217 alpha-3）—— 前端传当前展示币种，
     * 下单时按 I61 汇率快照进订单展示参考；未传 = CNY 不快照（两列 NULL），
     * 快照降级同样跳过（fail-open）。可选字段不加 {@code @NotNull}；
     * 格式由 {@code @Size} 前置拦截（→ 400 PARAM_ERROR）。
     */
    @Size(max = Order.DISPLAY_CURRENCY_MAX_LENGTH,
            message = "展示币种代码不能超过" + Order.DISPLAY_CURRENCY_MAX_LENGTH + "字符")
    private String displayCurrency;
}
