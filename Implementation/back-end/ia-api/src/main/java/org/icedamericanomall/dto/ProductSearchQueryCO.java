package org.icedamericanomall.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 商品检索查询 DTO —— search-service → item-service（Feign，ADR-052 退出直读的数据面归属）。
 *
 * <p>字段与 search-service 领域值对象 {@code SearchQuery} 对齐：关键词 / 分类 / 品牌 /
 * 在售 SKU 最低价区间（分）/ 排序（sales|price|newest|relevance，item 侧白名单化） / 分页。
 */
@Data
public class ProductSearchQueryCO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 关键词（可空 = 全量浏览；item 侧对 name/description LIKE） */
    private String keyword;

    /** 分类过滤（可空） */
    private Long categoryId;

    /** 品牌精确过滤（SE1，可空） */
    private String brand;

    /** 最低价过滤（可空，分，含边界；口径 = 在售 SKU 最低价） */
    private Integer minPrice;

    /** 最高价过滤（可空，分，含边界） */
    private Integer maxPrice;

    /** 排序：sales|price|newest|relevance（可空 = 默认相关度即销量降序） */
    private String sort;

    /** 页码（1 起） */
    private int page = 1;

    /** 每页条数 */
    private int size = 10;
}
