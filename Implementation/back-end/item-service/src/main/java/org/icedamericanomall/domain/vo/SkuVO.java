package org.icedamericanomall.domain.vo;

import lombok.Data;

import java.io.Serializable;

@Data
public class SkuVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String skuId;
    private Long productId;
    private String spec;
    private Integer price;
    private Integer stock;
    private String image;
    private Integer soldCount;
    private Integer status;

    // V4.0: 库存类型 + 热销标识 + 销售标签
    private Integer stockType;
    private Boolean isHot;
    private String hotReason;
    private String salesTags;
}
