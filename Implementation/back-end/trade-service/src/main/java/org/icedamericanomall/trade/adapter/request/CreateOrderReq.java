package org.icedamericanomall.trade.adapter.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.icedamericanomall.trade.domain.model.order.Order;

import java.io.Serializable;

/**
 * 购物车下单请求（{@code POST /api/trade/order}）。
 *
 * <p><b>契约收缩（V5.3 → V5.4）</b>：{@code cartItemIds} 为从未被任何代码路径读取的
 * 死字段，已删除 —— 下单商品恒取自服务端购物车（按登录用户读取已勾选项），请求体不承载购物车项 ID。
 * V5.4 再删 {@code userCouponIds}：Controller 只装配单券 {@code userCouponId}，
 * 多券叠加（计价能力已就绪：{@code PricingService.validateStackRules}，ADR-043）属 roadmap，需 PRD 决策后再接线，
 * 在此之前请求体不暴露该字段，避免「声明了却不生效」的静默忽略。
 *
 * <p><b>V5.7 重新立项</b>：{@code remark} 作为真实买家留言回归（V5.4 删除的是从未接线的
 * 死字段）—— 随单快照落 {@code orders.remark} 并经 {@code OrderVO} 回显，Phase 1 不提供修改端点。
 *
 * <p>入参对象化后 {@code addressId} 为必填：策略 {@code NormalCartOrderStrategy.validate}
 * 以地址前置条件失败抛 {@code PARAM_ERROR}，此处用 Bean Validation 前置同一约束（错误码/HTTP 状态一致）。
 */
@Data
public class CreateOrderReq implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 收货地址 ID（必填）。 */
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
     * 下单时按 I61 汇率快照进订单展示参考（{@code orders.display_currency/display_rate}）；
     * 未传 = CNY 不快照（两列 NULL），快照降级同样跳过（fail-open，主流程零影响）。
     * 可选字段不加 {@code @NotNull}；格式由 {@code @Size} 前置拦截（→ 400 PARAM_ERROR）。
     */
    @Size(max = Order.DISPLAY_CURRENCY_MAX_LENGTH,
            message = "展示币种代码不能超过" + Order.DISPLAY_CURRENCY_MAX_LENGTH + "字符")
    private String displayCurrency;
}
