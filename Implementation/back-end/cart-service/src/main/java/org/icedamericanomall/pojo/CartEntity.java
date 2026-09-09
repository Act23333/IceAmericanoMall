package org.icedamericanomall.pojo;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("cart")
public class CartEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long skuId;

    /** Enriched at read time from item-service — not persisted in cart table. */
    @TableField(exist = false)
    private String productName;

    /** Enriched at read time from item-service — not persisted in cart table. */
    @TableField(exist = false)
    private Long productId;

    /** Enriched at read time from item-service — not persisted in cart table. */
    @TableField(exist = false)
    private String skuSpec;

    /** Enriched at read time from item-service — not persisted in cart table. */
    @TableField(exist = false)
    private Integer price;

    /** Enriched at read time from item-service — not persisted in cart table. */
    @TableField(exist = false)
    private String image;

    private Integer quantity;

    private Boolean selected;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
