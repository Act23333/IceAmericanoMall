package org.icedAmericanoMall.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class SkuDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long skuId;
    private Long productId;
    private String productName;
    private String spec;
    private Integer price;
    private Integer stock;
    private String image;
    private Integer status;
}
