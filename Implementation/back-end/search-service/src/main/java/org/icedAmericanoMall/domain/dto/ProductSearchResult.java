package org.icedAmericanoMall.domain.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 商品搜索结果 DTO。
 */
@Data
public class ProductSearchResult implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long productId;
    private String name;
    private String description;
    private Long categoryId;
    /** 价格（分） */
    private Integer price;
    private String image;
    private Integer soldCount;
}
