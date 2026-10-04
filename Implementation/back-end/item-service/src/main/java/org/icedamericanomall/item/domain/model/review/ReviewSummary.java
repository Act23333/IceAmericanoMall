package org.icedamericanomall.item.domain.model.review;

import java.util.Map;

/**
 * 评价摘要值对象（领域读模型，不可变）—— 均分 + 星级分布 + 总数。
 *
 * @param averageScore 平均分（保留 1 位小数；无评价时为 0.0）
 * @param totalCount   参与统计的评价总数（脏星级数据不计入）
 * @param distribution 星级 → 评价数（固定含 1~5 星，缺省为 0）
 */
public record ReviewSummary(
        double averageScore,
        long totalCount,
        Map<Integer, Long> distribution
) {

    /** 无评价时的空摘要（分布仍固定含 1~5 星，均为 0）。 */
    public static ReviewSummary empty(Map<Integer, Long> zeroDistribution) {
        return new ReviewSummary(0.0, 0L, zeroDistribution);
    }
}
