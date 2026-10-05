package org.icedamericanomall.trade.adapter.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.icedamericanomall.trade.domain.model.order.Order;

import java.io.Serializable;

/**
 * F13 拼团下单请求（T27, ADR-049 两段式参团第一步）—— quantity 恒 1（一人一单一件），
 * 拼团价与活动绑定 SKU 均由 marketing I52 服务端下发（请求体不含价格/SKU/券，防篡改）。
 *
 * <p>下单不占团位：未支付订单走既有 30 分钟超时取消；支付成功后由 trade 自动发起 I53 参团/开团。
 */
@Data
public class GroupBuyOrderReq implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 拼团活动 id（必填；活动绑定 SKU 由 I52 服务端下发，不信任客户端传入）。 */
    @NotNull(message = "拼团活动ID不能为空")
    private Long activityId;

    /** 团号（可选；空 = 开新团，非空 = 加入该团）。 */
    private String teamNo;

    @NotNull(message = "地址ID不能为空")
    private Long addressId;

    /**
     * 买家留言（可选，≤ {@link Order#REMARK_MAX_LENGTH}）—— 下单时随单快照落库。
     * 可选字段不加 {@code @NotBlank}；超长由 {@code @Size} 前置拦截（→ 400 PARAM_ERROR）。
     */
    @Size(max = Order.REMARK_MAX_LENGTH, message = "买家留言不能超过" + Order.REMARK_MAX_LENGTH + "字")
    private String remark;
}
