package org.icedamericanomall.search.domain.model.search;

import org.icedamericanomall.search.domain.enums.SearchSortEnum;

/**
 * 商品搜索查询（领域值对象）—— 对网关接口屏蔽 Web 层参数形态。
 *
 * @param keyword    关键词（可为空 = 全量浏览）
 * @param categoryId 分类过滤（可空）
 * @param page       页码（1 起）
 * @param size       每页条数
 * @param brand      品牌精确过滤（SE1，可空）—— ES term（忽略大小写）/ DB eq，两链同口径
 * @param minPrice   最低价过滤（SE1，可空，分，含边界）—— 口径为在售 SKU 最低价
 * @param maxPrice   最高价过滤（SE1，可空，分，含边界）—— 口径为在售 SKU 最低价
 * @param sort       排序取值 sales|price|newest|relevance（SE1，可空 = relevance 默认相关度）
 */
public record SearchQuery(String keyword, Long categoryId, int page, int size,
                          String brand, Integer minPrice, Integer maxPrice, String sort) {

    /** 历史调用兼容构造（仅 keyword/category/分页，不带 SE1 过滤参数）。 */
    public SearchQuery(String keyword, Long categoryId, int page, int size) {
        this(keyword, categoryId, page, size, null, null, null, null);
    }

    /** 关键词是否有效（非空非空白）。 */
    public boolean hasKeyword() {
        return keyword != null && !keyword.isBlank();
    }

    /** 品牌过滤是否有效（非空非空白）。 */
    public boolean hasBrand() {
        return brand != null && !brand.isBlank();
    }

    /** 是否带价格区间过滤（min/max 至少一个非空；min &gt; max 时结果自然为空集，不做前置校验）。 */
    public boolean hasPriceRange() {
        return minPrice != null || maxPrice != null;
    }

    /** 排序方式（宽松解析，null/未知回落 relevance = 默认相关度即现行为）。 */
    public SearchSortEnum sortOrDefault() {
        return SearchSortEnum.fromValue(sort);
    }
}
