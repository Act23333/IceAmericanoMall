package org.icedAmericanoMall.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("order_item")
public class OrderItemEntity implements Serializable {

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

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
