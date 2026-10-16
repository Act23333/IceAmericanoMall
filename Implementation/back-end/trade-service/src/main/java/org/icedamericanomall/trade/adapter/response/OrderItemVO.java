package org.icedamericanomall.trade.adapter.response;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 订单项视图对象 —— 下单时点的商品快照（不引用商品/SKU 活表）。
 *
 * <p>{@code id} 保留：前端订单列表/详情以它作为渲染 key（见 web {@code shop/orders} 与
 * 小程序 {@code order-detail}/{@code orders} 页），移除会破坏列表渲染稳定性。
 * {@code orderId}（外键）与审核字段未暴露。
 */
@Data
public class OrderItemVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 订单项 ID（渲染 key）。 */
    private Long id;

    private Long skuId;

    /** 商品名快照。 */
    private String productName;

    /** 规格快照。 */
    private String skuSpec;

    /** 成交单价快照（分）。 */
    private Integer price;

    private Integer quantity;

    /** 小计（分）= price * quantity。 */
    private Integer subTotal;

    /** 图片快照。 */
    private String image;

    /**
     * 归属包裹号（V5.6 拆单发货增量）—— 对应物流记录 {@code packageNo}（{@code /api/logistics/{orderId}}）；
     * {@code null} = 尚未发货。字段上线前报文不含该键，前端须按可空字段消费。
     */
    private Integer packageNo;
}
