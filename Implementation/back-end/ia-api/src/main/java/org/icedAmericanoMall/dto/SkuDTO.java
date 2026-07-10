package org.icedAmericanoMall.dto;

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
}
