package org.icedAmericanoMall.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 商品搜索结果 Feign DTO — ai-service SearchTool 使用。
 * <p>
 * 映射 search-service ProductSearchVO 的核心字段。
 */
@Data
public class ProductSearchResult implements Serializable {

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
