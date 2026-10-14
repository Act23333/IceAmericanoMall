package org.icedamericanomall.item.domain.model.product;

/**
 * 商品检索条件（领域值对象，ADR-052）—— 内部检索出口的入参归一形态。
 *
 * <p>语义与 search-service 的 {@code SearchQuery} 对齐：关键词 LIKE（name/description）、
 * 分类/品牌过滤、在售 SKU 最低价区间（分，含边界）、排序（sales|price|newest|relevance，未知回落销量降序）。
 *
 * @param keyword    关键词（可空 = 全量浏览）
 * @param categoryId 分类过滤（可空）
 * @param brand      品牌精确过滤（可空）
 * @param minPrice   最低价（分，可空）
 * @param maxPrice   最高价（分，可空；min &gt; max 时结果自然为空集）
 * @param sort       排序（可空/未知 = relevance 现行为：销量降序）
 * @param page       页码（1 起）
 * @param size       每页条数（调用方已夹取上限）
 */
public record ProductSearchCriteria(String keyword, Long categoryId, String brand,
                                    Integer minPrice, Integer maxPrice, String sort,
                                    int page, int size) {
}
