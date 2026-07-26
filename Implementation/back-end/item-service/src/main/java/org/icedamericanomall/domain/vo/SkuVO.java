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
}
