package org.icedamericanomall.trade.adapter.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * 购物车下单请求（{@code POST /api/trade/order}）。
 *
 * <p><b>契约收缩（V5.3 → V5.4）</b>：{@code cartItemIds} 与 {@code remark} 为从未被任何代码路径读取的
 * 死字段，已删除 —— 下单商品恒取自服务端购物车（按登录用户读取已勾选项），请求体不承载购物车项 ID。
 * V5.4 再删 {@code userCouponIds}：Controller 只装配单券 {@code userCouponId}，
 * 多券叠加（策略层 {@code validateStackRules} 能力已就绪）属 roadmap，需 PRD 决策后再接线，
 * 在此之前请求体不暴露该字段，避免「声明了却不生效」的静默忽略。
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
}
