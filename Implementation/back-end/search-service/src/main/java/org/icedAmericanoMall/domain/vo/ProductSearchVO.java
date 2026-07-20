package org.icedAmericanoMall.domain.vo;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;

import java.io.Serializable;

/**
 * 商品搜索结果 VO — 映射 ES products 索引
 */
@Data
@Document(indexName = "products")
public class ProductSearchVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
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
    /** V2.5.2: 商品描述 Embedding 向量 (dense_vector, 1536维) */
    private double[] embedding;
}
