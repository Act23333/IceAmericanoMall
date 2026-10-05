package org.icedamericanomall.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 商品向量摄取入参（item-service → search-service，契约 §4.14.10 I47，V5.9）。
 *
 * <p>幂等覆盖写：同 {@code productId} 重写整份商品向量文档（ADR-046 单向量不分块）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductVectorUpsertCO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 商品 id（幂等键） */
    @NotNull
    private Long productId;

    /** 商品名称（向量化文本主来源） */
    @NotBlank
    private String name;

    /** 商品描述（向量化文本） */
    private String description;

    /** 品牌 */
    private String brand;

    /** 类目 id */
    private Long categoryId;

    /** 主图 */
    private String mainImage;

    /** 展示价（分，取最小 SKU 价等由调用方决定） */
    private Integer price;

    /** 销量 */
    private Integer soldCount;
}
