package org.icedamericanomall.trade.domain.model.order;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 订单项聚合（纯 POJO）—— {@code order_item} 表，下单快照（商品名/规格/单价/图片）。
 */
@Data
public class OrderItem {

    private Long id;
    private Long orderId;
    private Long skuId;

    /** 商品名快照。 */
    private String productName;

    /** 规格快照。 */
    private String skuSpec;

    /** 单价快照（分）。 */
    private Integer price;

    private Integer quantity;

    /** 小计（分）。 */
    private Integer subTotal;

    /** 图片快照。 */
    private String image;

    /**
     * 归属包裹号（V5.6 拆单发货，F3）—— 发货时标记，对应 {@code order_logistics.package_no}；
     * {@code null} = 未发货；一明细只发一次（重复发货同一 sku 被拒）。
     */
    private Integer packageNo;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
