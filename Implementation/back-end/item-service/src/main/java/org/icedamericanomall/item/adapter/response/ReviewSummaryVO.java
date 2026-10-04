package org.icedamericanomall.item.adapter.response;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;

/**
 * 商品评价摘要（均分 + 星级分布）。
 */
@Data
public class ReviewSummaryVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 平均分，保留 1 位小数；无评价时为 0.0。 */
    private Double averageScore;

    /** 评价总数。 */
    private Long totalCount;

    /** 星级 → 评价数（固定包含 1~5 星，无评价的星级为 0）。 */
    private Map<Integer, Long> distribution;
}
