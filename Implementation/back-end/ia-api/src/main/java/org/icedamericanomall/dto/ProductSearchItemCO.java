package org.icedamericanomall.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 商品检索命中条目 DTO —— item-service 内部检索端点返回（Feign，ADR-052）。
 *
 * <p>字段与 search-service 领域读模型 {@code ProductSearchHit} 的展示面一致；
 * {@code productId} 为业务标识 32 位 hex（item 域在持久化边界做 BINARY(16) ↔ hex 转换，ADR-050）；
 * {@code minPrice} 为在售 SKU 最低价（分）；{@code publishTime} 为 epoch millis（SE1 newest 排序回填用）。
 */
@Data
public class ProductSearchItemCO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 商品技术主键 */
    private Long id;

    /** 业务商品编号（32 位 hex，对外契约口径） */
    private String productId;

    /** 分类ID */
    private Long categoryId;

    /** 商品名 */
    private String name;

    /** 描述 */
    private String description;

    /** 品牌 */
    private String brand;

    /** 主图 */
    private String mainImage;

    /** 在售 SKU 最低价（分；无在售 SKU 时为 0，与历史 DB 链口径一致） */
    private Integer minPrice;

    /** 销量 */
    private Integer soldCount;

    /** 上架时间（epoch millis；ES 重建/回填用，可空） */
    private Long publishTime;
}
