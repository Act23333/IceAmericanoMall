package org.icedamericanomall.search.adapter.response;

import lombok.Data;

import java.util.List;

/**
 * 搜索 facet 视图（SE6）—— 品牌/价格档位计数，供 C 端过滤 UI。
 *
 * <p>价格档位 min/max 单位为<b>分</b>（整数分硬约束），固定档位换算：0-50/50-100/100-200/200-500/500+ 元；
 * 末档无上界（{@code max = null}）。
 */
@Data
public class SearchFacetsVO {

    /** 品牌计数桶（count 降序）。 */
    private List<BrandFacetVO> brands;

    /** 价格档位计数桶。 */
    private List<PriceRangeFacetVO> priceRanges;

    /** 品牌 facet。 */
    @Data
    public static class BrandFacetVO {

        /** 品牌名。 */
        private String name;

        /** 命中商品数。 */
        private long count;
    }

    /** 价格档位 facet。 */
    @Data
    public static class PriceRangeFacetVO {

        /** 档位下界（分，含）。 */
        private Integer min;

        /** 档位上界（分，不含；末档无上界为 null）。 */
        private Integer max;

        /** 命中商品数。 */
        private long count;
    }
}
