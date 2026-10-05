package org.icedamericanomall.search.domain.model.search;

import java.util.List;

/**
 * 搜索 facet 计数（领域读模型，SE6）—— 反映 keyword/categoryId 过滤前的分布，供 C 端过滤 UI；
 * DB LIKE 链无聚合能力（facet 是 ES 能力），返回 {@link #empty()}。
 *
 * @param brands      品牌计数桶（按 count 降序，条数上限见 ES terms 聚合 size）
 * @param priceRanges 价格档位计数桶（固定档位，min/max 单位分）
 */
public record SearchFacets(List<BrandFacet> brands, List<PriceRangeFacet> priceRanges) {

    /** 空 facet（DB 链降级 / ES 聚合异常降级的统一返回，不阻断 C 端搜索）。 */
    public static SearchFacets empty() {
        return new SearchFacets(List.of(), List.of());
    }

    /** 品牌 facet 桶。 */
    public record BrandFacet(String name, long count) {
    }

    /**
     * 价格档位 facet 桶（min/max 单位为分，含下界不含上界；末档无上界，{@code max = null}）。
     */
    public record PriceRangeFacet(Integer min, Integer max, long count) {
    }
}
