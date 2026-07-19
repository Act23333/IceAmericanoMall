package org.icedAmericanoMall.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * V3.0: 商品详情 Feign DTO — AI Subagent 使用。
 * 映射 item-service ProductVO 核心字段。
 */
@Data
public class ProductDetailDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String productId;
    private String name;
    private String description;
    private String brand;
    private Long categoryId;
    private String mainImage;
    private String images;          // JSON array string
    private String attributes;      // JSON object string
    private Integer soldCount;
    private Integer status;
}
