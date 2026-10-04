package org.icedamericanomall.trade.infrastructure.persistence.order;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("order_item")
public class OrderItemPO implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;

    private Long skuId;

    /** 商品名快照 */
    private String productName;

    /** 规格快照 */
    private String skuSpec;

    /** 单价快照（分） */
    private Integer price;

    private Integer quantity;

    /** 小计（分） */
    private Integer subTotal;

    /** 图片快照 */
    private String image;

    /**
     * 归属包裹号（V5.6 拆单发货，F3）—— 发货时标记，对应 {@code order_logistics.package_no}；
     * {@code null} = 未发货；一明细只发一次（重复发货同一 sku 被拒，{@code ORDER_STATUS_INVALID(3010)}）。
     */
    private Integer packageNo;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
