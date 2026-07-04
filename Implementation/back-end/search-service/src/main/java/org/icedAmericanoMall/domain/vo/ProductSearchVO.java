package org.icedAmericanoMall.domain.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 商品搜索结果 VO
 */
@Data
public class ProductSearchVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String productId;
    private Long categoryId;
    private String name;
    private String description;
    private String brand;
    private String mainImage;
    /** 最低 SKU 价格 (分) */
    private Integer price;
    private Integer soldCount;
}
