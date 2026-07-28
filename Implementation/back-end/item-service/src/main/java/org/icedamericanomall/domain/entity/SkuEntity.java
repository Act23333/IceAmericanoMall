package org.icedamericanomall.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("sku")
public class SkuEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String skuId;

    private Long productId;

    private String spec;

    private Integer price;
    private Integer originalPrice; // V3.5: 原价(分)

    private Integer stock;

    private String image;

    private Integer soldCount;

    // V4.0: 库存类型 + 热销标识 + 销售标签（京东/淘宝标准）
    private Integer stockType;   // 1=LIMITED, 2=UNLIMITED, 3=PRESALE
    private Boolean isHot;       // 热销标识
    private String hotReason;    // discount/new_arrival/best_seller/clearance
    private String salesTags;    // JSON: ["限时优惠","新品"]

    private Integer status;

    @Version
    private Integer version;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
