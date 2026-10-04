package org.icedamericanomall.search.infrastructure.es;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;

import java.io.Serializable;

/**
 * 商品 ES 索引文档（基础设施层）——映射 ES {@code products} 索引。
 *
 * <p>索引字段与对外视图 {@link org.icedamericanomall.search.adapter.response.ProductSearchVO}
 * 分离：本文档可包含检索实现专用字段（如 {@code embedding} 稠密向量），
 * 对外 VO 只保留必要字段，二者由 {@code ProductSearchConverter} 转换。
 */
@Data
@Document(indexName = "products")
public class ProductDocument implements Serializable {

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
    /** V2.5.2: 商品描述 Embedding 向量 (dense_vector, 1536维)，仅索引内部使用 */
    private double[] embedding;
}
