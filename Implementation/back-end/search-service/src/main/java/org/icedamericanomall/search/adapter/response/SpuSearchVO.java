package org.icedamericanomall.search.adapter.response;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 商品搜索结果视图对象（对外契约）。
 *
 * <p>与 ES 索引文档 {@link org.icedamericanomall.search.infrastructure.es.SpuDocument}
 * 解耦：只暴露前端检索结果列表所需字段；ES 内部字段（如 {@code embedding} 稠密向量）
 * 属于实现细节，不外泄。字段名与历史 JSON 契约保持一致。
 */
@Data
public class SpuSearchVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private String spuId;
    private Long categoryId;
    /** SPU 名 —— 线上键 {@code spu_name}（契约 §1.8④；SE1 与 I28 同形，原 {@code name}）。 */
    private String spuName;
    private String description;
    private String brand;
    private String mainImage;
    /** 最低 SKU 价格 (分) */
    private Integer price;
    private Integer soldCount;
}
