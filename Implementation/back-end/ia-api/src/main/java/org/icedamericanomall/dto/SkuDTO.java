package org.icedamericanomall.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
public class SkuDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;          // 技术主键 (sku.id)
    private Long productId;
    private Long sellerId;
    private String productName;
    private String spec;
    private Integer price;
    private Integer stock;
    private String image;
    private Integer status;

    // V4.0: 库存类型 + 热销标识 + 销售标签（京东/淘宝标准）
    private Integer stockType;   // 1=LIMITED, 2=UNLIMITED, 3=PRESALE
    private Boolean isHot;
    private String hotReason;
    private String salesTags;
}
